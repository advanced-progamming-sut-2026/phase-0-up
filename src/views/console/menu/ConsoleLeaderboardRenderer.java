package views.console.menu;

import models.leaderboard.LbColumn;
import models.leaderboard.LeaderboardEntry;
import views.renderers.MenuRenderer.LeaderboardRenderer;

import java.util.List;

// Renders the already-sorted rows as an aligned, boxed table with a rank column, marking which column
// the board is currently sorted on and in which direction.
public class ConsoleLeaderboardRenderer implements LeaderboardRenderer {

    private static final String[] HEADERS = {
            "Rank", "Username", "Stage", "Mini-games", "Daily Quests", "Non-Daily Quests", "Meow Points"
    };

    // Render the whole board. sortedBy/ascending only affect the caption and the little arrow next to
    // the active column header; the row ordering is whatever the caller already sorted into `entries`.
    @Override
    public void renderLeaderboard(List<LeaderboardEntry> entries, LbColumn sortedBy, boolean ascending) {
        if (entries == null || entries.isEmpty()) {
            System.out.println("=============== LEADERBOARD ===============");
            System.out.println("No registered players to show yet.");
            return;
        }

        String[][] rows = buildRows(entries);
        String[] headers = markSortedColumn(sortedBy, ascending);
        int[] widths = measureColumns(headers, rows);

        System.out.println(buildTable(headers, rows, widths, sortedBy, ascending));
    }

    private String[][] buildRows(List<LeaderboardEntry> entries) {
        String[][] rows = new String[entries.size()][HEADERS.length];
        for (int i = 0; i < entries.size(); i++) {
            LeaderboardEntry e = entries.get(i);
            rows[i][0] = String.valueOf(i + 1);
            rows[i][1] = e.getUsername();
            rows[i][2] = e.getStageLabel();
            rows[i][3] = String.valueOf(e.getMinigamesCompleted());
            rows[i][4] = String.valueOf(e.getDailyQuests());
            rows[i][5] = String.valueOf(e.getNonDailyQuests());
            rows[i][6] = String.valueOf(e.getBestMeowPoint());
        }
        return rows;
    }

    private String[] markSortedColumn(LbColumn sortedBy, boolean ascending) {
        int sortedColumn = columnIndex(sortedBy);
        String[] headers = HEADERS.clone();
        if (sortedColumn >= 0) {
            headers[sortedColumn] = HEADERS[sortedColumn] + (ascending ? " ^" : " v");
        }
        return headers;
    }

    private int[] measureColumns(String[] headers, String[][] rows) {
        int[] widths = new int[HEADERS.length];
        for (int c = 0; c < HEADERS.length; c++) {
            widths[c] = headers[c].length();
            for (String[] row : rows) {
                widths[c] = Math.max(widths[c], row[c] == null ? 0 : row[c].length());
            }
        }
        return widths;
    }

    private String buildTable(String[] headers, String[][] rows, int[] widths,
                              LbColumn sortedBy, boolean ascending) {
        String separator = buildSeparator(widths);
        StringBuilder sb = new StringBuilder();
        sb.append("=============== LEADERBOARD ===============\n");
        if (sortedBy != null) {
            sb.append("Sorted by ").append(sortedBy.getDisplayName())
                    .append(ascending ? " (ascending)" : " (descending)").append("\n");
        }
        sb.append(separator).append("\n");
        sb.append(formatRow(headers, widths)).append("\n");
        sb.append(separator).append("\n");
        for (String[] row : rows) {
            sb.append(formatRow(row, widths)).append("\n");
        }
        sb.append(separator);
        return sb.toString();
    }

    // Feedback when the player names a column that does not exist.
    @Override
    public void unknownColumn(String token) {
        System.out.println("Unknown column '" + token + "'. Sort by one of: "
                + "stage, minigames, daily, nondaily, score.");
    }

    // Feedback when the sort order flag is neither ascending nor descending.
    @Override
    public void unknownOrder(String token) {
        System.out.println("Unknown order '" + token + "'. Use 'asc' or 'desc'.");
    }

    private int columnIndex(LbColumn column) {
        if (column == null) {
            return -1;
        }
        return switch (column) {
            case LEVEL -> 2;
            case MINIGAMES -> 3;
            case DAILY_QUESTS -> 4;
            case NONDAILY_QUESTS -> 5;
            case MEOW_POINT -> 6;
        };
    }

    private String formatRow(String[] cells, int[] widths) {
        StringBuilder sb = new StringBuilder("|");
        for (int c = 0; c < cells.length; c++) {
            String cell = cells[c] == null ? "" : cells[c];
            sb.append(' ').append(cell);
            for (int pad = cell.length(); pad < widths[c]; pad++) {
                sb.append(' ');
            }
            sb.append(" |");
        }
        return sb.toString();
    }

    private String buildSeparator(int[] widths) {
        StringBuilder sb = new StringBuilder("+");
        for (int width : widths) {
            for (int i = 0; i < width + 2; i++) {
                sb.append('-');
            }
            sb.append('+');
        }
        return sb.toString();
    }
}
