package views.gdx.ui;

import views.gdx.ui.NpcDialogueBox.Speaker;

import java.util.LinkedHashMap;
import java.util.Map;

// Which of the model's sentences an NPC says out loud, and who says it.
//
// ## Why this listens rather than being told
//
// The same seam every other event-driven effect uses -- the explosions, the weather, the camera shake,
// the audio cues and the death effects all subscribe to `GameScreen.onModelEvent` and ignore anything
// that is not their own sentence. Nothing in the model knows this exists, which is what keeps a
// cosmetic feature out of the tick loop and stops the two front ends' text drifting apart: every line
// here is a sentence the terminal build prints verbatim.
//
// ## What is worth interrupting for, and what is NOT
//
// Two triggers, chosen because in both cases a toast is genuinely the wrong container:
//
//   * **A special mode's opening banner.** Every GameMode announces its rules in `onStart` -- and those
//     are three or four lines explaining how this board differs from every other one. In the toast
//     stack that arrives as a wall of text that starts fading immediately, on the one screen where the
//     player has not yet learnt the rules. Penny explaining it is what that sentence was written for.
//   * **"The final wave has come."** One line, easy to miss among spawn notices, and the only moment in
//     a level with any drama to it. Zomboss gets it.
//
// Deliberately NOT the win and loss lines, which would be the obvious third and fourth: the result
// PANEL goes up on the same frame and says the same thing, and two announcements of one event read as a
// bug rather than as emphasis.
//
// ## The banners are matched by prefix, and that is a choice
//
// A mode's banner opens with the mode's own name, which is a convention the modes already keep. Prefix
// matching is therefore stable against the rest of the sentence changing -- and when a banner IS
// reworded past its opening, the line simply stays a toast. Degrading to what it did before is the
// right failure for a flourish; nothing breaks and nothing is lost.
public final class NpcLines {

    // "The final wave has come." -- WaveSystem.launch, for a wave flagged final. Matched whole because
    // it is a fixed string with nothing interpolated into it.
    private static final String FINAL_WAVE = "The final wave has come.";

    // The opening word of every mode banner, in the modes' own spelling. Values are all Penny for now;
    // the map is keyed by speaker rather than being a list so a future mode can be given to Dave without
    // restructuring anything.
    private static final Map<String, Speaker> BANNERS = buildBanners();

    private static Map<String, Speaker> buildBanners() {
        Map<String, Speaker> m = new LinkedHashMap<>();
        m.put("Beghouled!", Speaker.PENNY);
        m.put("Dead Line!", Speaker.PENNY);
        m.put("I, Zombie!", Speaker.PENNY);
        m.put("Night Ops!", Speaker.PENNY);
        m.put("Vasebreaker!", Speaker.PENNY);
        m.put("Wall-nut Bowling!", Speaker.PENNY);
        m.put("Scoring Game for", Speaker.PENNY);
        m.put("Zombotany!", Speaker.PENNY);
        // The one banner Penny does not deliver. A boss level's opening line is the boss ARRIVING, and
        // the machine announcing itself is the whole point of the moment -- Penny explaining the rules
        // of a fight the player can already see rolling onto the lawn throws it away. Keyed on the mode
        // name rather than on the four boss names for the same reason every other entry here is: the
        // banner opens with it (see ZombossMode.onStart), so one token covers all four seasons.
        m.put("Zomboss!", Speaker.ZOMBOSS);
        return m;
    }

    private NpcLines() { }

    // The speaker for this sentence, or null for the overwhelming majority that nobody says aloud.
    public static Speaker speakerFor(String message) {
        if (message == null) {
            return null;
        }
        String line = message.trim();
        if (line.equals(FINAL_WAVE)) {
            return Speaker.ZOMBOSS;
        }
        for (Map.Entry<String, Speaker> banner : BANNERS.entrySet()) {
            if (line.startsWith(banner.getKey())) {
                return banner.getValue();
            }
        }
        return null;
    }
}
