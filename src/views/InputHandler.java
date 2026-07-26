package views;

import java.util.Scanner;

public class InputHandler {
    private static final Scanner SCANNER = new Scanner(System.in);

    // Returns the next trimmed line, or null once stdin is exhausted (EOF / closed pipe). Callers
    // treat null as "no more input" and stop their read loop, rather than spinning forever on "".
    public static String readLine() {
        if (SCANNER.hasNextLine()) {
            return SCANNER.nextLine().trim();
        }
        return null;
    }

}
