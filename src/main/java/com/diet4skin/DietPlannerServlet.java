package com.diet4skin;

import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.util.*;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import jakarta.servlet.RequestDispatcher;

@WebServlet("/DietPlannerServlet")
public class DietPlannerServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private String dbUrl;
    private String dbUser;
    private String dbPass;

    @Override
    public void init() throws ServletException {
        // Load database properties from db.properties
        Properties props = new Properties();
        try (InputStream input = getServletContext().getResourceAsStream("/WEB-INF/db.properties")) {
            if (input == null) {
                throw new ServletException("db.properties file not found in WEB-INF folder!");
            }
            props.load(input);
            dbUrl = props.getProperty("db.url");
            dbUser = props.getProperty("db.user");
            dbPass = props.getProperty("db.password");

            // Load MySQL JDBC driver
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (Exception e) {
            throw new ServletException("Failed to load DB config", e);
        }
    }

    // -------------------------------------------------------------
    //  GET: Show initial form (list of problems)
    // -------------------------------------------------------------
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        List<String[]> problems = loadSkinProblems();
        request.setAttribute("problems", problems);

        // Forward to JSP page for rendering
        RequestDispatcher rd = request.getRequestDispatcher("/dietplanner.jsp");
        rd.forward(request, response);
    }

    // -------------------------------------------------------------
    //  POST: Generate 7-day plan based on selected problems
    // -------------------------------------------------------------
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String[] selectedProblems = request.getParameterValues("problems");
        Map<String, List<String>> weeklyPlan = new LinkedHashMap<>();

        if (selectedProblems != null && selectedProblems.length > 0) {

            // Build placeholders for IN clause dynamically (e.g., ?,?,?)
            StringBuilder placeholders = new StringBuilder();
            for (int i = 0; i < selectedProblems.length; i++) {
                placeholders.append("?");
                if (i < selectedProblems.length - 1)
                    placeholders.append(",");
            }

            String sql = "SELECT DISTINCT d.item_name, d.type " +
                    "FROM diet_items d " +
                    "JOIN problem_diet_map m ON d.id = m.diet_item_id " +
                    "WHERE m.problem_id IN (" + placeholders.toString() + ")";

            List<String> liquids = new ArrayList<>();
            List<String> nonLiquids = new ArrayList<>();

            try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass);
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                for (int i = 0; i < selectedProblems.length; i++) {
                    ps.setInt(i + 1, Integer.parseInt(selectedProblems[i]));
                }

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String type = rs.getString("type");
                        String itemName = rs.getString("item_name");
                        String item = itemName + " (" + type + ")";

                        if ("liquid".equalsIgnoreCase(type)) {
                            if (!liquids.contains(item))
                                liquids.add(item);
                        } else {
                            if (!nonLiquids.contains(item))
                                nonLiquids.add(item);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            // Shuffle for randomness
            Collections.shuffle(liquids);
            Collections.shuffle(nonLiquids);

            Iterator<String> liquidIter = cycleIterator(new ArrayList<>(liquids), true);
            Iterator<String> nonIter = cycleIterator(new ArrayList<>(nonLiquids), false);

            String[] days = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};

            for (String day : days) {
                List<String> daily = new ArrayList<>();

                // 1 liquid
                if (!liquids.isEmpty() && liquidIter.hasNext()) {
                    daily.add(liquidIter.next());
                }

                // 4 non-liquids or fallback
                for (int i = 0; i < 4; i++) {
                    if (!nonLiquids.isEmpty()) {
                        daily.add(nonIter.next());
                    } else if (!liquids.isEmpty()) {
                        daily.add(liquidIter.next());
                    }
                }

                weeklyPlan.put(day, daily);
            }
        }

        // Fetch problems again for left panel
        List<String[]> problems = loadSkinProblems();

        // Set attributes for JSP
        request.setAttribute("problems", problems);
        request.setAttribute("weeklyPlan", weeklyPlan);
        request.setAttribute("selectedProblems", selectedProblems);

        // Forward to JSP
        RequestDispatcher rd = request.getRequestDispatcher("/plan.jsp");
        rd.forward(request, response);
    }

    // -------------------------------------------------------------
    //  Helper: Load all skin problems for the checkbox list
    // -------------------------------------------------------------
    private List<String[]> loadSkinProblems() {
        List<String[]> problems = new ArrayList<>();
        String sql = "SELECT id, problem_name FROM skin_problems ORDER BY problem_name";

        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass);
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                problems.add(new String[]{
                        String.valueOf(rs.getInt("id")),
                        rs.getString("problem_name")
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return problems;
    }

    // -------------------------------------------------------------
    //  Helper: Iterator that cycles through list elements
    // -------------------------------------------------------------
    private Iterator<String> cycleIterator(List<String> list, boolean repeatAllowed) {
        return new Iterator<String>() {
            int index = 0;

            @Override
            public boolean hasNext() {
                return !list.isEmpty();
            }

            @Override
            public String next() {
                if (list.isEmpty()) return null;

                if (index >= list.size()) {
                    index = 0;
                    if (!repeatAllowed) {
                        Collections.shuffle(list);
                    }
                }
                return list.get(index++);
            }
        };
    }
}
