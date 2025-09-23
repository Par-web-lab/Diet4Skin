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
        // Render the selection page (left panel + empty right panel)
        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");

        List<String[]> problems = loadSkinProblems();

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>");
        html.append("<html lang='en'><head>");
        html.append("<meta charset='UTF-8'>");
        html.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        html.append("<title>Skin Diet Planner</title>");
        // Use external style2.css if present, but include minimal grid styles in head to ensure boxes display
        html.append("<link rel='stylesheet' href='style2.css'>");

        // Inline styles for the day-box grid (ensures 3 boxes per row)
        html.append("<style>");
        html.append(".plan-grid{display:flex;flex-wrap:wrap;gap:18px;}");
        html.append(".day-box{box-shadow:0 2px 6px rgba(0,0,0,0.08);background:#fff;border-radius:8px;padding:14px;flex:0 1 calc(33.333% - 18px);box-sizing:border-box;min-width:220px;}");
        html.append(".day-box h3{margin:0 0 8px 0;color:#1f4f6b;font-size:18px;}");
        html.append(".day-box ul{margin:0;padding-left:20px;}");
        html.append("@media (max-width:900px){ .day-box{flex:0 1 calc(50% - 18px);} }");
        html.append("@media (max-width:600px){ .day-box{flex:0 1 100%;} }");
        html.append("</style>");

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
        html.append("<div class='plan-grid'>"); // empty grid
        html.append("</div>");
        html.append("</div>");

        html.append("</div></body></html>");

        response.getWriter().write(html.toString());
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Main plan generation and rendering
        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");

        String[] selectedProblems = request.getParameterValues("problems");
        Map<String, List<String>> weeklyPlan = new LinkedHashMap<>();

        if (selectedProblems != null && selectedProblems.length > 0) {
            // Query DB for diet items related to selected problems using PreparedStatement
            // Build placeholders for IN clause
            StringBuilder placeholders = new StringBuilder();
            for (int i = 0; i < selectedProblems.length; i++) {
                placeholders.append("?");
                if (i < selectedProblems.length - 1) placeholders.append(",");
            }

            String sql = "SELECT DISTINCT d.item_name, d.type " +
                         "FROM diet_items d " +
                         "JOIN problem_diet_map m ON d.id = m.diet_item_id " +
                         "WHERE m.problem_id IN (" + placeholders.toString() + ")";

            List<String> liquids = new ArrayList<>();
            List<String> nonLiquids = new ArrayList<>();

            try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass);
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                // set parameters (assume problem ids are integers)
                for (int i = 0; i < selectedProblems.length; i++) {
                    ps.setInt(i + 1, Integer.parseInt(selectedProblems[i]));
                }

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String type = rs.getString("type");
                        String itemName = rs.getString("item_name");
                        String item = itemName + " (" + type + ")";
                        if ("liquid".equalsIgnoreCase(type)) {
                            if (!liquids.contains(item)) liquids.add(item);
                        } else {
                            if (!nonLiquids.contains(item)) nonLiquids.add(item);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            // Shuffle lists for randomness
            Collections.shuffle(liquids);
            Collections.shuffle(nonLiquids);

            // Create iterators: liquids may repeat earlier (repeatAllowed=true),
            // nonLiquids won't repeat until exhausted (repeatAllowed=false)
            Iterator<String> liquidIter = cycleIterator(new ArrayList<>(liquids), true);
            Iterator<String> nonIter = cycleIterator(new ArrayList<>(nonLiquids), false);

            // Days order
            String[] days = {"Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday"};

            for (String day : days) {
                List<String> daily = new ArrayList<>();

                // 1 liquid (if exists) - if none, skip liquid
                if (!liquids.isEmpty() && liquidIter.hasNext()) {
                    daily.add(liquidIter.next());
                }

                // Add 4 non-liquids. If nonLiquids is empty, fill from liquids (if available).
                for (int i = 0; i < 4; i++) {
                    if (!nonLiquids.isEmpty()) {
                        if (nonIter.hasNext()) {
                            daily.add(nonIter.next());
                        } else {
                            // iterator always hasNext because hasNext returns !list.isEmpty()
                            daily.add(nonIter.next());
                        }
                    } else if (!liquids.isEmpty()) {
                        // fallback: use liquids to fill if no non-liquids defined
                        if (liquidIter.hasNext()) daily.add(liquidIter.next());
                    }
                }

                weeklyPlan.put(day, daily);
            }
        }

        // Build response HTML with grid of day-boxes (3 per row by CSS)
        List<String[]> problems = loadSkinProblems();
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang='en'><head>");
        html.append("<meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        html.append("<title>Skin Diet Planner</title>");
        html.append("<link rel='stylesheet' href='style2.css'>");

        // Inline grid CSS (same as doGet) to ensure boxes layout even without external CSS changes
        html.append("<style>");
        html.append(".plan-grid{display:flex;flex-wrap:wrap;gap:18px;}");
        html.append(".day-box{box-shadow:0 2px 6px rgba(0,0,0,0.08);background:#fff;border-radius:8px;padding:14px;flex:0 1 calc(33.333% - 18px);box-sizing:border-box;min-width:220px;}");
        html.append(".day-box h3{margin:0 0 8px 0;color:#1f4f6b;font-size:18px;}");
        html.append(".day-box ul{margin:0;padding-left:20px;}");
        html.append("@media (max-width:900px){ .day-box{flex:0 1 calc(50% - 18px);} }");
        html.append("@media (max-width:600px){ .day-box{flex:0 1 100%;} }");
        html.append("</style>");

        html.append("</head><body>");
        html.append("<div class='header'><h1>🩺 Skin Diet Planner</h1></div>");
        html.append("<div class='main'>");

        // Left panel (problems + form)
        html.append("<div class='left-panel'>");
        html.append("<h2>Select Skin Problem(s):</h2>");
        html.append("<form method='post' action='DietPlannerServlet'>");
        for (String[] problem : problems) {
            String checked = "";
            // If user previously selected, preserve check? (optional) - skipped for simplicity
            html.append("<label><input type='checkbox' name='problems' value='")
                .append(problem[0]).append("'> ")
                .append(problem[1]).append("</label><br>");
        }
        html.append("<button type='submit'>Generate 7-Day Plan</button>");
        html.append("</form>");
        html.append("</div>");

        // Right panel (the grid of day boxes)
        html.append("<div class='right-panel'>");
        html.append("<h2>7-Day Personalized Diet Plan</h2>");
        html.append("<div class='plan-grid'>");

        // Render each day as a day-box. The CSS ensures 3 boxes per row (wrap).
        for (Map.Entry<String, List<String>> entry : weeklyPlan.entrySet()) {
            html.append("<div class='day-box'>");
            html.append("<h3>").append(entry.getKey()).append("</h3>");
            html.append("<ul>");
            for (String item : entry.getValue()) {
                html.append("<li>").append(escapeHtml(item)).append("</li>");
            }
            html.append("</ul>");
            html.append("</div>");
        }

        // If weeklyPlan is empty (user didn't select problems or no items), show friendly message
        if (weeklyPlan.isEmpty()) {
            html.append("<div style='padding:16px;background:#fff;border-radius:8px;box-shadow:0 2px 6px rgba(0,0,0,0.06);'>");
            html.append("<p>Please choose one or more skin problems on the left and click <strong>Generate 7-Day Plan</strong>.</p>");
            html.append("</div>");
        }

        html.append("</div>"); // .plan-grid
        html.append("</div>"); // .right-panel

        html.append("</div></body></html>");

        response.getWriter().write(html.toString());
    }

    /**
     * Load skin problems for the checkboxes.
     * Returns list of {id, problem_name}
     */
    private List<String[]> loadSkinProblems() {
        List<String[]> problems = new ArrayList<>();
        String sql = "SELECT id, problem_name FROM skin_problems ORDER BY problem_name";
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass);
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                problems.add(new String[]{String.valueOf(rs.getInt("id")), rs.getString("problem_name")});
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return problems;
    }

    /**
     * Returns an iterator that cycles through a list.
     * If repeatAllowed is false, it reshuffles after exhausting all items to avoid fixed repetition patterns.
     */
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
                        // reshuffle after each full cycle to give fresh order next cycle
                        Collections.shuffle(list);
                    }
                }
                return list.get(index++);
            }
        };
    }

    /**
     * Simple HTML-escape for items to avoid accidental injection into the page.
     */
    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
