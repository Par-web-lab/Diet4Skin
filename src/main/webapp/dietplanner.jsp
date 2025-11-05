<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.*, java.util.Map, java.util.List" %>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Skin Diet Planner</title>
    <link rel="stylesheet" href="style2.css?v=<%= System.currentTimeMillis() %>">
    
</head>
<body>
    
    <div class="main">
        <div class="left-panel">
            <h2>Select Skin Problem(s):</h2>
            <form method="post" action="DietPlannerServlet">
                <%
                    List<String[]> problems = (List<String[]>) request.getAttribute("problems");
                    if (problems != null) {
                        for (String[] problem : problems) {
                %>
                    <label>
                        <input type="checkbox" name="problems" value="<%= problem[0] %>"> <%= problem[1] %>
                    </label><br>
                <%
                        }
                    }
                %>
                <button type="submit">Generate 7-Day Plan</button>
            </form>
        </div>

        <div class="right-panel">
            <h2>7-Day Personalized Diet Plan</h2>
            <p>Select problems and click “Generate 7-Day Plan”.</p>
        </div>
    </div>
</body>
</html>
