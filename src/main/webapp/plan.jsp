<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.*, java.util.Map, java.util.List" %>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>7-Day Plan</title>
    <link rel="stylesheet" href="style2.css?v=<%= System.currentTimeMillis() %>">
    <style>
        .plan-grid{display:flex;flex-wrap:wrap;gap:18px;}
        .day-box{box-shadow:0 2px 6px rgba(0,0,0,0.08);background:#fff;border-radius:8px;padding:14px;
                 flex:0 1 calc(33.333% - 18px);box-sizing:border-box;min-width:220px;}
        .day-box h3{margin:0 0 8px 0;color:#1f4f6b;font-size:18px;}
        .day-box ul{margin:0;padding-left:20px;}
        @media (max-width:900px){ .day-box{flex:0 1 calc(50% - 18px);} }
        @media (max-width:600px){ .day-box{flex:0 1 100%;} }
    </style>
</head>
<body>


    <div class="main">
        <div class="left-panel">
            <h2>Select Skin Problem(s):</h2>
            <form method="post" action="DietPlannerServlet">
                <%
                    List<String[]> problems = (List<String[]>) request.getAttribute("problems");
                    String[] selected = (String[]) request.getAttribute("selectedProblems");
                    if (problems != null) {
                        for (String[] p : problems) {
                            boolean checked = false;
                            if (selected != null) {
                                for (String s : selected) {
                                    if (s.equals(p[0])) { checked = true; break; }
                                }
                            }
                %>
                    <label>
                        <input type="checkbox" name="problems" value="<%= p[0] %>"
                            <%= checked ? "checked" : "" %>> <%= p[1] %>
                    </label><br>
                <%
                        }
                    }
                %>
                <button type="submit">Generate Again</button>
            </form>
        </div>

        <div class="right-panel">
            <h2>7-Day Personalized Diet Plan</h2>
            <div class="plan-grid">
                <%
                    Map<String, List<String>> plan = (Map<String, List<String>>) request.getAttribute("weeklyPlan");
                    if (plan != null && !plan.isEmpty()) {
                        for (Map.Entry<String, List<String>> entry : plan.entrySet()) {
                %>
                    <div class="day-box">
                        <h3><%= entry.getKey() %></h3>
                        <ul>
                            <%
                                for (String item : entry.getValue()) {
                            %>
                                <li><%= item %></li>
                            <%
                                }
                            %>
                        </ul>
                    </div>
                <%
                        }
                    } else {
                %>
                    <p>No plan generated. Please select at least one problem.</p>
                <%
                    }
                %>
            </div>
        </div>
    </div>
</body>
</html>
