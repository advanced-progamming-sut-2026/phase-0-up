package views.renderers.MenuRenderer;

import models.leaderboard.LbColumn;
import models.leaderboard.LeaderboardEntry;

import java.util.List;

// Presentation for the leaderboard menu. Renders the already-sorted rows as an aligned, boxed table
// with a rank column, marking which column the board is currently sorted on and in which direction.
public class LeaderboardRenderer {

    private static final String[] HEADERS = {
            "Rank", "Username", "Stage", "Mini-games", "Daily Quests", "Non-Daily Quests", "Mu-Points"
    };

    // Render the whole board. sortedBy/ascending only affect the caption and the little arrow next to
    // the active column header; the row ordering is whatever the caller already sorted into `entries`.
    public void renderLeaderboard(List<LeaderboardEntry> entries, LbColumn sortedBy, boolean ascending) {
        if (entries == null || entries.isEmpty()) {
            System.out.println("=============== LEADERBOARD ===============");
            System.out.println("No registered players to show yet.");
            return;
        }

        String[][] rows = new String[entries.size()][HEADERS.length];
        for (int i = 0; i < entries.size(); i++) {
            LeaderboardEntry e = entries.get(i);
            rows[i][0] = String.valueOf(i + 1);
            rows[i][1] = e.getUsername();
            rows[i][2] = e.getStageLabel();
            rows[i][3] = String.valueOf(e.getMinigamesCompleted());
            rows[i][4] = String.valueOf(e.getDailyQuests());
            rows[i][5] = String.valueOf(e.getNonDailyQuests());
            rows[i][6] = String.valueOf(e.getBestMyoPoint());
        }

        int sortedColumn = columnIndex(sortedBy);
        String[] headers = HEADERS.clone();
        if (sortedColumn >= 0) {
            headers[sortedColumn] = HEADERS[sortedColumn] + (ascending ? " ^" : " v");
        }

        int[] widths = new int[HEADERS.length];
        for (int c = 0; c < HEADERS.length; c++) {
            widths[c] = headers[c].length();
            for (String[] row : rows) {
                widths[c] = Math.max(widths[c], row[c] == null ? 0 : row[c].length());
            }
        }

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
        System.out.println(sb.toString());
    }

    // Feedback when the player names a column that does not exist.
    public void unknownColumn(String token) {
        System.out.println("Unknown column '" + token + "'. Sort by one of: "
                + "stage, minigames, daily, nondaily, score.");
    }

    // Feedback when the sort order flag is neither ascending nor descending.
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
            case MYOPOINT -> 6;
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
