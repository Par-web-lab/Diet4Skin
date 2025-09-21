package com.diet4skin;

import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.util.*;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

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

            Class.forName("com.mysql.cj.jdbc.Driver"); // Load MySQL driver
        } catch (Exception e) {
            throw new ServletException("Failed to load DB config", e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");

        List<String[]> problems = loadSkinProblems();

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>");
        html.append("<html lang='en'><head>");
        html.append("<meta charset='UTF-8'>");
        html.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        html.append("<title>Skin Diet Planner</title>");
        html.append("<link rel='stylesheet' href='style2.css'>");
        html.append("</head><body>");

        // Header
        html.append("<div class='header'>");
        html.append("<h1>🩺 Skin Diet Planner</h1>");
        html.append("</div>");

        // Main Layout
        html.append("<div class='main'>");

        // Left panel (problems list)
        html.append("<div class='left-panel'>");
        html.append("<h2>Select Skin Problem(s):</h2>");
        html.append("<form method='post' action='DietPlannerServlet'>");

        for (String[] problem : problems) {
            html.append("<label>");
            html.append("<input type='checkbox' name='problems' value='").append(problem[0]).append("'> ");
            html.append(problem[1]);
            html.append("</label><br>");
        }

        html.append("<button type='submit'>Generate 7-Day Plan</button>");
        html.append("</form>");
        html.append("</div>");

        // Right panel (empty initially)
        html.append("<div class='right-panel'>");
        html.append("<h2>7-Day Personalized Diet Plan</h2>");
        html.append("</div>");

        html.append("</div></body></html>");

        response.getWriter().write(html.toString());
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");

        String[] selectedProblems = request.getParameterValues("problems");
        Map<String, List<String>> weeklyPlan = new LinkedHashMap<>();

        if (selectedProblems != null && selectedProblems.length > 0) {
            try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass);
                 Statement stmt = conn.createStatement()) {

                String inClause = String.join(",", selectedProblems);
                String sql = "SELECT d.item_name, d.type " +
                             "FROM diet_items d " +
                             "JOIN problem_diet_map m ON d.id = m.diet_item_id " +
                             "WHERE m.problem_id IN (" + inClause + ")";

                ResultSet rs = stmt.executeQuery(sql);

                List<String> liquids = new ArrayList<>();
                List<String> nonLiquids = new ArrayList<>();

                while (rs.next()) {
                    String item = rs.getString("item_name") +
                                  " (" + rs.getString("type") + ")";
                    if ("liquid".equalsIgnoreCase(rs.getString("type"))) {
                        liquids.add(item);
                    } else {
                        nonLiquids.add(item);
                    }
                }
                rs.close();

                // Shuffle for variation
                Collections.shuffle(liquids);
                Collections.shuffle(nonLiquids);

                // 7-day plan
                String[] days = {"Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday"};
                int liqIndex = 0, nonIndex = 0;

                for (String day : days) {
                    List<String> daily = new ArrayList<>();
                    // Ensure 1 liquid
                    if (!liquids.isEmpty()) {
                        daily.add(liquids.get(liqIndex % liquids.size()));
                        liqIndex++;
                    }
                    // Add 4 non-liquids
                    for (int i = 0; i < 4; i++) {
                        if (!nonLiquids.isEmpty()) {
                            daily.add(nonLiquids.get(nonIndex % nonLiquids.size()));
                            nonIndex++;
                        }
                    }
                    weeklyPlan.put(day, daily);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Build HTML
        List<String[]> problems = loadSkinProblems();
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang='en'><head>");
        html.append("<meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        html.append("<title>Skin Diet Planner</title>");
        html.append("<link rel='stylesheet' href='style2.css'>");
        html.append("</head><body>");

        html.append("<div class='header'><h1>🩺 Skin Diet Planner</h1></div>");
        html.append("<div class='main'>");

        // Left panel
        html.append("<div class='left-panel'>");
        html.append("<h2>Select Skin Problem(s):</h2>");
        html.append("<form method='post' action='DietPlannerServlet'>");
        for (String[] problem : problems) {
            html.append("<label><input type='checkbox' name='problems' value='")
                .append(problem[0]).append("'> ")
                .append(problem[1]).append("</label><br>");
        }
        html.append("<button type='submit'>Generate 7-Day Plan</button>");
        html.append("</form></div>");

        // Right panel
        html.append("<div class='right-panel'>");
        html.append("<h2>7-Day Personalized Diet Plan</h2>");
        html.append("<div class='grid'>");

        for (Map.Entry<String, List<String>> entry : weeklyPlan.entrySet()) {
            html.append("<div class='day-box'>");
            html.append("<h3>").append(entry.getKey()).append("</h3><ul>");
            for (String item : entry.getValue()) {
                html.append("<li>").append(item).append("</li>");
            }
            html.append("</ul></div>");
        }

        html.append("</div></div></div></body></html>");
        response.getWriter().write(html.toString());
    }

    private List<String[]> loadSkinProblems() {
        List<String[]> problems = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, problem_name FROM skin_problems")) {
            while (rs.next()) {
                problems.add(new String[]{String.valueOf(rs.getInt("id")), rs.getString("problem_name")});
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return problems;
    }
}
