/*
 * ============================================================================
 *  Battleground2028.java  -  "BATTLEGROUND 2028: The Race for the White House"
 *  A presidential campaign simulation for BlueJ (single file, Java 8 or newer).
 * ============================================================================
 *
 *  HOW TO RUN IN BLUEJ
 *    1. Project > New Project (any name).
 *    2. Edit > Add Class from File...  and choose this file  (or create a class
 *       named  Battleground2028  and paste the whole file into it).
 *    3. Compile, then right-click Battleground2028 and choose
 *       void main(String[] args).
 *
 *  WHAT IT IS
 *    Run a 2028 presidential campaign as a Democrat or a Republican, from the
 *    week after the 2026 midterms through the primaries, the conventions and
 *    the general election - or start straight at the general - or run as an
 *    independent and win your ballot lines state by state.  Each turn is one
 *    week (one day in the final two weeks): set the candidate's itinerary,
 *    book real podcasts, buy TV and digital ads, open field offices and hire
 *    organisers, court bundlers, commission polls, pick an issue focus and the
 *    week's message, make (or avoid) policy promises, bid for endorsements when
 *    rivals drop out, fight the debates line by line, and pick a running mate.
 *
 *    The electorate is modelled in every state as 96 voter cells (religion and
 *    race, education, age and sex) with overlays for union, veteran, gun-owning,
 *    LGBTQ, rural/suburban/urban, student and parent voters, calibrated to the
 *    2020, 2022 and 2024 results and projected to 2028.  Candidates are rated
 *    from their public records; ratings are the game's estimates, not facts,
 *    and nothing in the game quotes a real person.  Everything that happens in
 *    a game is fiction.
 *
 *  CONTROLS
 *    Click a state to select it; the right panel shows its voters, polls and
 *    your operation there (ads by media market, digital, offices, organisers).
 *    Right-click a state to send the candidate.  The left panel holds the
 *    Schedule, Money, Field, Message, Polls, Campaign and News tabs.  The map
 *    toggles between a blank map, your spending, your internal polls, the
 *    public polls and your volunteers and doors (and, for an independent,
 *    ballot access).  Mouse wheel scrolls lists.
 *    Space ... end the week    1-7 ... tabs    Esc ... deselect    F1 ... briefing
 *    Debates: keys 1-6 pick your approach to each question, then 1-3 the line
 *    you deliver; 1-4 answer a rival's attack.  Esc goes back a step.
 *
 *  SAVES
 *    The game autosaves every week to a folder named .battleground2028 in your
 *    home directory; "Continue" on the main menu picks up where you left off.
 * ============================================================================
 */

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class Battleground2028 extends JPanel implements ActionListener, MouseListener, MouseMotionListener, KeyListener, MouseWheelListener {
    private static final long serialVersionUID = 1L;

    // =====================================================================
    //  1.  CONSTANTS AND SMALL HELPERS
    // =====================================================================
    static final int DW = 1600, DH = 900;      // design resolution; everything is scaled to the window

    // parties
    static final int DEM = 0, REP = 1, GRN = 2, LBT = 3, FWD = 4, AMP = 5, IND = 6;
    static final String[] PARTY = {"Democratic", "Republican", "Green", "Libertarian", "Forward", "America Party", "Independent"};
    static final String[] PARTY_SHORT = {"D", "R", "G", "L", "F", "A", "I"};
    static final Color C_IND = new Color(0xf5, 0x9e, 0x0b);

    static double clamp(double v, double lo, double hi) { return v < lo ? lo : v > hi ? hi : v; }

    static int clampi(int v, int lo, int hi) { return v < lo ? lo : v > hi ? hi : v; }

    static double ease(double t) { t = clamp(t, 0, 1); return t * t * (3 - 2 * t); }

    static double easeOut(double t) { t = clamp(t, 0, 1); return 1 - (1 - t) * (1 - t) * (1 - t); }

    static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    static double logistic(double x) { return 1 / (1 + Math.exp(-x)); }

    static double logit(double p) { p = clamp(p, 1e-6, 1 - 1e-6); return Math.log(p / (1 - p)); }

    static Color mix(Color a, Color b, double t) {
        t = clamp(t, 0, 1);
        return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t),
                (int) (a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }

    static Color alpha(Color c, double a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) clamp(a * 255, 0, 255));
    }

    static Color shade(Color c, double f) {
        if (f < 1) return new Color((int) (c.getRed() * f), (int) (c.getGreen() * f), (int) (c.getBlue() * f), c.getAlpha());
        return mix(c, Color.white, f - 1);
    }

    static Color rgb(int hex) { return new Color((hex >> 16) & 255, (hex >> 8) & 255, hex & 255); }

    static String fmt1(double v) { return String.format("%.1f", v); }

    static String fmt0(double v) { return String.valueOf(Math.round(v)); }

    static String pct0(double v) { return Math.round(v * 100) + "%"; }

    static String pct1(double v) { return String.format("%.1f%%", v * 100); }

    static String signed1(double v) { if (Math.abs(v) < 0.05) return "0.0"; return (v >= 0 ? "+" : "") + String.format("%.1f", v); }

    /** A two-party margin in points, written D+3.2 / R+1.5. */
    static String margin(double dMinusR) {
        if (Math.abs(dMinusR) < 0.05) return "TIED";
        return (dMinusR > 0 ? "D+" : "R+") + String.format("%.1f", Math.abs(dMinusR));
    }

    static String thousands(long v) {
        String s = String.valueOf(Math.abs(v));
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) b.append(',');
            b.append(s.charAt(i));
        }
        return (v < 0 ? "-" : "") + b;
    }

    /** Money in dollars, compact: $950K, $12.4M, $1.21B. */
    static String money(double d) {
        double a = Math.abs(d);
        String s;
        if (a >= 1e9) s = String.format("$%.2fB", a / 1e9);
        else if (a >= 1e7) s = String.format("$%.1fM", a / 1e6);
        else if (a >= 1e6) s = String.format("$%.2fM", a / 1e6);
        else if (a >= 1e3) s = String.format("$%.0fK", a / 1e3);
        else s = String.format("$%.0f", a);
        return (d < 0 ? "-" : "") + s;
    }

    static String compact(double v) {
        double a = Math.abs(v);
        if (a >= 1e6) return String.format("%.1fM", v / 1e6);
        if (a >= 1e4) return String.format("%.0fK", v / 1e3);
        if (a >= 1e3) return String.format("%.1fK", v / 1e3);
        return String.valueOf(Math.round(v));
    }

    // ---- palette: "war room" ---------------------------------------------------------------------------
    static final Color C_BG = rgb(0x060a13);
    static final Color C_BG2 = rgb(0x0b1220);
    static final Color C_PANEL = new Color(13, 21, 36, 236);
    static final Color C_PANEL2 = new Color(19, 30, 50, 240);
    static final Color C_CARD = new Color(22, 34, 56, 245);
    static final Color C_LINE = new Color(46, 66, 98, 200);
    static final Color C_LINE2 = new Color(80, 110, 160, 220);
    static final Color C_TEXT = rgb(0xe7edf7);
    static final Color C_DIM = rgb(0x93a4bf);
    static final Color C_FAINT = rgb(0x56688a);
    static final Color C_GOLD = rgb(0xf2c14e);
    static final Color C_ACCENT = rgb(0x7cc4ff);
    static final Color C_DEM = rgb(0x2f7bf6);
    static final Color C_DEM_L = rgb(0x79acff);
    static final Color C_DEM_D = rgb(0x163d86);
    static final Color C_REP = rgb(0xe5484d);
    static final Color C_REP_L = rgb(0xff8a8d);
    static final Color C_REP_D = rgb(0x8a1f26);
    static final Color C_GREEN = rgb(0x2bd47d);
    static final Color C_MONEY = rgb(0x1fbf6a);
    static final Color C_THIRD = rgb(0xa78bfa);
    static final Color C_AMBER = rgb(0xffb347);
    static final Color C_BAD = rgb(0xff5d5d);
    static final Color C_GOOD = rgb(0x45d483);
    static final Color C_TEAL = rgb(0x2dd4bf);
    static final Color C_STATE = rgb(0x2a3650);
    static final Color C_STATE_EDGE = rgb(0x0a1020);

    static Color partyColor(int p) {
        switch (p) {
            case DEM: return C_DEM;
            case REP: return C_REP;
            case GRN: return rgb(0x3fbf5f);
            case LBT: return rgb(0xf5c542);
            case FWD: return rgb(0x8b5cf6);
            case IND: return C_IND;
            default: return rgb(0x22c3d6);
        }
    }

    static Color partyLight(int p) { return p == DEM ? C_DEM_L : p == REP ? C_REP_L : shade(partyColor(p), 1.3); }

    /** Diverging map colour for a D-minus-R margin in points (positive = Democratic). */
    static Color marginColor(double m, double confidence) {
        double a = clamp(Math.abs(m) / 20.0, 0, 1);
        Color base = m >= 0 ? C_DEM : C_REP;
        Color lite = m >= 0 ? rgb(0x9cc2ff) : rgb(0xffb0b2);
        Color dark = m >= 0 ? rgb(0x0f3a8f) : rgb(0x8f1d22);
        Color c = a < 0.5 ? mix(lite, base, a * 2) : mix(base, dark, (a - 0.5) * 2);
        if (Math.abs(m) < 1.0) c = mix(rgb(0xb9a6d9), c, Math.abs(m));
        return mix(C_STATE, c, clamp(confidence, 0.15, 1));
    }

    // =====================================================================
    //  SOUND  -  every effect is synthesised in code
    // =====================================================================
    static final class Sfx implements Runnable {
        static final int RATE = 22050;
        static final int CLICK = 0, HOVER = 1, CHIME = 2, CHEER = 3, TURN = 4, CASH = 5, ALERT = 6, ERROR = 7, OPEN = 8,
                CALL = 9, GAVEL = 10, WHOOSH = 11, BELL = 12, STING = 13, COUNT = 14;

        static final class Voice {
            float[] d;
            double pos, rate;
            float vol;
        }

        final float[][] lib = new float[COUNT][];
        final ArrayList<Voice> voices = new ArrayList<Voice>();
        final Random rnd = new Random(11);
        volatile boolean running, enabled = true;
        volatile float volume = 0.6f;
        SourceDataLine line;

        Sfx() { build(); }

        void start() {
            try {
                AudioFormat fmt = new AudioFormat(RATE, 16, 1, true, false);
                SourceDataLine l = AudioSystem.getSourceDataLine(fmt);
                l.open(fmt, 4096);
                l.start();
                line = l;
                running = true;
                Thread t = new Thread(this, "bg2028-audio");
                t.setDaemon(true);
                t.start();
            } catch (Throwable t) {
                line = null;          // no audio device: the game simply runs silently
            }
        }

        void stop() {
            running = false;
            try { if (line != null) { line.stop(); line.close(); } } catch (Throwable t) { /* ignore */ }
        }

        void play(int id, double vol, double pitch) {
            if (!enabled || line == null || id < 0 || id >= COUNT || lib[id] == null) return;
            Voice v = new Voice();
            v.d = lib[id];
            v.rate = pitch;
            v.vol = (float) vol;
            synchronized (voices) {
                if (voices.size() > 16) voices.remove(0);
                voices.add(v);
            }
        }

        public void run() {
            final int n = 256;
            float[] mixBuf = new float[n];
            byte[] out = new byte[n * 2];
            while (running) {
                Arrays.fill(mixBuf, 0f);
                synchronized (voices) {
                    for (int vi = voices.size() - 1; vi >= 0; vi--) {
                        Voice v = voices.get(vi);
                        float[] d = v.d;
                        for (int i = 0; i < n; i++) {
                            int p = (int) v.pos;
                            if (p >= d.length - 1) break;
                            double fr = v.pos - p;
                            mixBuf[i] += (float) ((d[p] * (1 - fr) + d[p + 1] * fr) * v.vol);
                            v.pos += v.rate;
                        }
                        if ((int) v.pos >= d.length - 1) voices.remove(vi);
                    }
                }
                float vol = enabled ? volume : 0f;
                for (int i = 0; i < n; i++) {
                    double s = mixBuf[i] * vol;
                    s = s / (1 + Math.abs(s) * 0.55);
                    int v = (int) (s * 30000);
                    out[2 * i] = (byte) v;
                    out[2 * i + 1] = (byte) (v >> 8);
                }
                try { line.write(out, 0, out.length); } catch (Throwable t) { running = false; }
            }
        }

        static int len(double s) { return Math.max(1, (int) (s * RATE)); }

        float[] noise(double secs, double fc0, double fc1, double decay, double attack) {
            float[] o = new float[len(secs)];
            double y1 = 0, y2 = 0;
            for (int i = 0; i < o.length; i++) {
                double t = i / (double) RATE, u = t / secs;
                double fc = fc0 * Math.pow(fc1 / fc0, u);
                double a = 1 - Math.exp(-2 * Math.PI * fc / RATE);
                y1 += a * (rnd.nextDouble() * 2 - 1 - y1);
                y2 += a * (y1 - y2);
                double env = Math.min(1, t / attack) * Math.exp(-t * decay);
                o[i] = (float) (y2 * env);
            }
            return o;
        }

        static float[] tone(double secs, double f0, double f1, int wave, double decay, double attack) {
            float[] o = new float[len(secs)];
            double ph = 0;
            for (int i = 0; i < o.length; i++) {
                double t = i / (double) RATE, u = t / secs;
                double f = f0 * Math.pow(f1 / f0, u);
                ph += 2 * Math.PI * f / RATE;
                double s;
                if (wave == 0) s = Math.sin(ph);
                else if (wave == 1) s = 2 / Math.PI * Math.asin(Math.sin(ph));
                else if (wave == 2) s = Math.tanh(3 * Math.sin(ph)) * 0.8;
                else s = (ph % (2 * Math.PI)) < Math.PI ? 0.6 : -0.6;
                double env = Math.min(1, t / attack) * Math.exp(-t * decay);
                o[i] = (float) (s * env);
            }
            return o;
        }

        static float[] mixAt(float[] a, float[] b, double gb, double atSecs) {
            int off = len(atSecs);
            float[] o = new float[Math.max(a.length, b.length + off)];
            for (int i = 0; i < a.length; i++) o[i] = a[i];
            for (int i = 0; i < b.length; i++) o[i + off] += (float) (b[i] * gb);
            return o;
        }

        static float[] norm(float[] a, double peak) {
            double m = 1e-9;
            for (float v : a) m = Math.max(m, Math.abs(v));
            for (int i = 0; i < a.length; i++) a[i] = (float) (a[i] / m * peak);
            return a;
        }

        void build() {
            lib[CLICK] = norm(mixAt(tone(0.04, 1900, 1500, 1, 90, 0.0008), noise(0.02, 6000, 3000, 160, 0.0005), 0.25, 0), 0.3);
            lib[HOVER] = norm(tone(0.03, 2600, 2400, 0, 120, 0.002), 0.06);
            float[] ch = mixAt(tone(0.9, 1046, 1046, 0, 4, 0.003), tone(0.9, 1568, 1568, 0, 5, 0.003), 0.6, 0.09);
            lib[CHIME] = norm(ch, 0.3);
            float[] cheer = noise(2.2, 900, 2500, 1.4, 0.25);
            for (int i = 0; i < 14; i++) cheer = mixAt(cheer, noise(0.06, 3000, 5000, 40, 0.004), 0.5, 0.2 + rnd.nextDouble() * 1.6);
            lib[CHEER] = norm(cheer, 0.38);
            lib[TURN] = norm(mixAt(tone(0.16, 659, 659, 0, 9, 0.004), tone(0.32, 988, 988, 0, 6, 0.004), 0.8, 0.11), 0.26);
            float[] cash = new float[1];
            for (int i = 0; i < 3; i++) cash = mixAt(cash, tone(0.09, 2637 + i * 300, 2637 + i * 300, 1, 25, 0.002), 0.8, i * 0.06);
            lib[CASH] = norm(cash, 0.25);
            float[] al = new float[1];
            for (int i = 0; i < 2; i++) al = mixAt(al, tone(0.14, 880, 880, 1, 10, 0.003), 1, i * 0.18);
            lib[ALERT] = norm(al, 0.26);
            lib[ERROR] = norm(tone(0.22, 220, 190, 2, 9, 0.005), 0.28);
            lib[OPEN] = norm(mixAt(noise(0.25, 900, 4000, 9, 0.01), tone(0.2, 520, 780, 1, 12, 0.004), 0.4, 0), 0.2);
            float[] call = mixAt(tone(0.5, 523, 523, 1, 4, 0.004), tone(0.5, 659, 659, 1, 4, 0.004), 0.8, 0.12);
            call = mixAt(call, tone(0.8, 784, 784, 1, 3, 0.004), 0.8, 0.24);
            lib[CALL] = norm(call, 0.32);
            lib[GAVEL] = norm(mixAt(noise(0.3, 2500, 300, 18, 0.001), tone(0.25, 180, 120, 0, 14, 0.001), 0.8, 0), 0.4);
            lib[WHOOSH] = norm(noise(0.5, 400, 3000, 5, 0.15), 0.18);
            lib[BELL] = norm(mixAt(tone(1.4, 784, 784, 0, 2.5, 0.002), tone(1.4, 1976, 1976, 0, 3.5, 0.002), 0.35, 0), 0.3);
            float[] st = mixAt(tone(0.25, 392, 392, 2, 4, 0.01), tone(0.25, 494, 494, 2, 4, 0.01), 0.7, 0);
            st = mixAt(st, tone(0.7, 587, 587, 2, 2.5, 0.01), 0.9, 0.22);
            lib[STING] = norm(st, 0.3);
        }
    }

    static Sfx sfx;

    static void snd(int id) { if (sfx != null) sfx.play(id, 1, 1); }

    static void snd(int id, double vol, double pitch) { if (sfx != null) sfx.play(id, vol, pitch); }

    // =====================================================================
    //  2.  MAP AND STATE DATA  (generated from public data; see BRIEFING > SOURCES)
    // =====================================================================
    /** State outlines in Albers USA projection (us-atlas / Census cartographic boundaries), quarter-pixel encoded. */
    static final String[][] SHAPES = {
            {"AL", "#D9#;-_KPMSRLQ>P~#Cz#6=!#F*#6,!#F`#85TWRZ]cMXXTD]QZJ`Q]UUQYNdNQPWUVSX!#Dm#:=M[XYYUQSKZVVKQNUKRRP@U8SPLSQYLOLCELHQKNHLKJMKSKnKOOMILEQ!#C}#9K!#D(#6FJLKI"},
            {"AK", "#+p#>UQLRQOKXFNLTMMXQMSSQMNUKMPRINNRJRGM~#+X#>lRJVJSPQSNFRLVPMLURLLVPNQVLRUMNOUTNLVHLRSEVTPIQNRMLPKIZGSTHMORHGYKKOIJKOM~#*N#?YSGSPGV~#*D#?LRLVQNSLKNVMLMRNI~#*K#>>PKULKVLO~#):#?dSMRTHOOL~#)H#=UWQSKXNORSOPSPZHOKR@BNJ~#(o#?wYNWGSQOMIKRLWNNRRPRLQRHUWLOQFRJTHNESKL~#)c#;^r@bJWPL[PT]PSQSNSQSIVPNKJMJPPJGCSMRTNSTWSNIFPJSLLMFP@HNBB>@ATNQKQEYQ]OXJSJT@^AUPUM]DUMTRYLYC]SPRJSOSROVESSPUSQRKVNZRMVVPNSWPQMUPYKXQRS"
                    + "QMUSdNZTaGlZ!#-}#=SWPOLWRSIXLOXXSRSc[TYWESNNERNMK[EXURWTOYYWQYUNQomNSUOPUTOQUSMgTQRTMSUQYUXM[KXLLMQHDQKIALNTVP[DNJKNCO]JNMLO>LUQQKSLJKONLPJSNLKSOGKNLEIEMKCLNOLWQEKGIMHNRGPA?JEJLNQUT]jINMKGRMHKIMRJMMLNQKNOQ]STWHTMRJKLPFK@C3FPKROLBMVHUEPFLQKJQ;N:VJVQJTKKLENRMKNPKITGKLPRJITPRFSPH[JMKKSPKIRKMWKIQLJPJKUMJMUMQMLKTIPQFLPMYSOLXRKRSMVTTLTLOMINUDOKSPKMJOVJNPYJKQRHXNOMJNTMOKVJONMISKLQJUMWEGTIKU@QDMJ_DUTSLYQ@"
                    + "GXELNJXEMISMTJPKTPSFVPSHVQRLVHOSPNTDSOSIQNZQMTPTRQRMSEWPSKV@UORKPPRIMMWJNOSLNITSNOTKUIPIULKJWNMKRPRGNLTUSISNRLKOTLMNQDQNUKRRGJNBYJMGTJNSGJOI[LNPSLMMSKNMFMRQROTJPKHLQRSIRRQQSJKJSFMISJQKMNJTMRI]KVPRUPGWO]@XJbLLUQRPKXSPMIJSGk>TQOITG]FR=RNMKQHVIOJ<VIIQHJSMUQUKRMNJDJILSIIOL6^LKRKPILEUIG8MPNSFREPHMOIC@RLIOVIOJMKROKINQLOOIKLOLRMMMKPPKTOLJVONJQK_?TCRLTNTQTVSN]CXRVNUGN@KILNQLTPSIJHLSKNHREYOIKJMQSSGKDNFQ@HM"
                    + "IPKKFRPSKBGHIOL~#(U#@%UJPLSLTQMLRKVNSQMSFRJTCR~#(|#<8RHPSXUVLSSOSUUZTLSHMIUNJCALNIQLLOI~#(O#=7RLNRXXHKKJ~#'K#?}dVFOEKNL~#'2#?yeOOLSMRSJQPRHP>J~#&j#?zUKPJTPMSTPNSLNGPLN~#&]#?sZOPLRPLTKONMHN~#&U#?jTPQSJTMKROLI~#$s#>YSNWTRVLMHNMHLN~#03#>rNGRPRUJP~#/h#>RQKUOOSLQJM~#/e#>rOKTMRQJWOKLO~#/`#>bQHMNOJVNWWXSVWINORRMUROTSLOYQLRYFPQLIMNILQPSLNMJKOWYQLSTLNRSKOHIKGKQNLUJ@GQMOILTMM~#/L#>JQKSNRSMJMMQL]N^YKSIPOQKOL"
                    + "RMLO_MSIEOIMOJG~#/5#=xUUXLWYQTGIVVRVH]MPMIPIHGE;~#/3#>@QHVQSOYcSbBBGKQNOJLMPJLPLL~#/1#>EPKSRPVKPPKLL~#.z#>3NJRLMKSRUIVRWPQRLQSQTXJQFKQUMSLO?B~#,m#=tZ;RSHZPQGR~#,l#=fPGSLM]LM"},
            {"AZ", "#-t#7?_OPKTLQIMCDLMIT@LLRJNKWNYEPDRNOARMNIWHRH]LZEC?LMPIL=JIKDU>LLUMPLP@L?SEMKP@TJKHPB^KTPOQXPRWTTUPYA!#/;#30!#1B#3Q!#36#3l!#2H#9P!#0c#99!#/*#8?!#-i#7RRDWG"},
            {"AR", "#>b#5$!#@T#4{!#B;#4sPVTPQSNWEXEb!#B^#5CVXFQORTRIQMSEQOTRRSMPSHSRSORJKKPOSQSMUMILOKSOSROPLRQMWKPPRSPQRLRTSLQLNMPMXHNNSTSOSCVMKLOR[NQLONILUPRUOPQISNJLQS[LTPVJROTMOOKIPQTHSOTJLMRSRTNNTILLRPQURMTHPQRLQNLJQUTKTQTSQDQOSQQSNQSKPJKJRVTOQHRSUKUTMOKRQOSJQRRSNPIRQIWOSRURJPQLSPVHPMRUSMT!#?7#8T!#?6#8#KMMQJLLRJMJRNLIMOKMP!#>q#6/!#>b#5$"},
            {"CA", "#*s#5q_ZRVMRFJOFJMMJ~#*i#6>QOT[VZIPKIK=~#*&#5qSPUUGMMJ~#)z#5#[TSTTPQMTQLRBOEKPJLJ~#)a#5$`PNRSQNSDPHD~#)R#4xVMSUDK~#(t#,A!#+v#-4!#+0#0/!#.H#5(MZSZTURbNURQ[`DYBRLVGVPULQO^LPNZEYGPPSLTRRJ_QUZRQ[MUJRNS@O!#+r#7*PFLIKPO@RKO8F9!#+C#5dIJNSIODIOLSJJ<>JJQMK<EJIMD><GMLPCKGHFK6KNHGGUFNISGLIUBPHBEMJTBNJHLH=GIOEHCODKLI=IIKFP@RHMMSKQSUKUCMBKJLPELJHKDMNLGSAOCKJV8WOMdUSX]RPLIO@KIPLJKRJOIKMMJ^KNIIJHPLWRSJSRTKMOUKOH"
                    + "ELOF@KMLRMMWBNHQLMKLON@EAJ?B8PLTGO1W@Q5E9OHKKG@QJOCo*SGMLQGUHW>P2KJUGQE"},
            {"CO", "#3h#/i!#6:#0)!#9$#0=!#8i#4D!#6H#43!#4w#4%!#36#3l!#3h#/i"},
            {"CT", "#N2#-G|EOSRNOK!#OF#-.XpTeLQQTNRIOCULNNSDTJOJSMNHPNRKNESNKC`LNJUHROSIREWHF^AII!#N2#-G"},
            {"DE", "#L}#0+VEULZPK[JSOTSQNW^]TXR]VTPUZWSQRMYn!#MA#1A!#L}#0+"},
            {"DC", "#L1#1+SHZWHZNIHI"},
            {"FL", "#KA#?p^FQQCWKN~#Je#@*SFg>[RQSSQCVNMGQAXEROM~#JS#@/ZKMSGQNM~#J##>&XaWRQMSPISFJG=~#GM#;8WJMSJRNM~#G&#:(UYS[SR!#IN#:3NQV`YMO=KGOCSOPKcS^PSMTQQ`!#JI#:o!#Jj#;J!#K6#;xTZKTOXUcdm!#Kt#=HWcRd!#L(#>hNJLNKRKaOUSVKVPRTKV;NYFi:k[@QAFO?WJMJPMSASILLJOEQN<2KPKH9GKTB;F6BGNKKPPRKPKBKLSLRRPBNILNPIGTSZGRNSLF!#IL#=;RRSLZ9RMRHLIIORWHNPLLGDKPTLRWQTWLSOUIQQVLPNGD@OCJ=QOR^RONHMMU5O@MFKMPLKGRJHGLILOOH@NKTKDHMJDHNNLGLLBEKA?"
                    + "3BESFNOQHVSVNRHOMMIQ5dPKLNIVEPTUTQg?JV;[@HIPKQHAQJPWSWRLNHFDHMEH8C3FDM1Q!#Dy#;#SMLNSLPISMHHSDMKEIFEQC!#G&#:("},
            {"GA", "#F*#6,!#H,#5oOTGVNUKRNUQSWTTOVVVSSMUPSXVWRWZ[SVaWWTUZ[T[XMWSRSPPTSRRNPTfZOUWWP]RSNQSSVPWVNRX[MTSSPVSPSNVSVPNTLRLNNSROKUINOSROMTKTJORSRNPULVIPOQTNQPI_P_HWOYJTUPPMPUM`QQ9O<KNSKOO[SWObEQH?PM!#G4#:AKLKCKKI@IHNGPMP;MEIIMBT?MDZBFJQFB<LDJG!#F*#6,"},
            {"HI", "#5%#?5OLWHRNRITKOIIENJQKRMXS]XZQaW`_NYWOQX^YOSA[BVAP=[KTKYITJJBILGQ<LBMNMDHC~#4G#>%RGVMSQUY[JXQXXYRPUJV3VJKM@KNLQGKIF~#4G#>HZHRQNUDPMM~#4)#>&UMWPVYMSDQKEKJ~#3n#=oTINKgRRLQS^NUQGWHQ=J<O~#2v#=C]NXETNZaNUUSPKRONTVYJRKMJQIK<OE>OKKK~#1O#<tTF_GSQQMVOTPSVLWOWEWBMIIGLMK~#1(#=)[BRONXHRLWKKOL"},
            {"ID", "#/%#)iNFSINCKJ!#/Y#&n!#09#&z!#0&#'zXaOUTUNRQUJRTYHOYZPSZVRYWZPWRORUMTRTTSOVTLSSNQPSXRVMQQJ[LOPUKSLYMOOXJNNSSRLSOTSTMPPTMRJNIURTOTJQOVTPUZTLQKVO[DYZLVSPMUQUMUV]OUSQNQSSNXKOPWUTOSVLUURXOUMPRWNUXZQMNKTJSN]UQNTSRHULYU`NQTXL[SQNMJSJOKSPQKSRTYNSTTPSSR!#2J#.S!#0B#.4!#.(#-c!#.M#+vY=MIVJNKLNPLJNLJKPLKRLMIRGTLY?XLXCPGYFV?c8LIOHHKNKKNI="},
            {"IL", "#A)#0}RNOELKSH_IPBWDP@CBR?]K^MSJWNWHPBWHQ=MD?FLKPK?BOL!#C]#.`NaRWY]UeUY!#D-#1oLPNVQSKTPTTSOVTQMXSVNWIUM[JSOQH`MMMRNMKWTUJURMORJRQULTSQIOUXF]RWTTNSARJTKMKSMYV[MVIP2BKPD]NUSSKOIHLORSNRHKOKHEOJJLQJSNPKGBNKRMLLOJDIOMAEKRLNMLRKJOHK<>MIPEY;PKLHTGPKEIAKIXLPIJKCQLK@8?KGKNLJBDOIIHQJIENGOETB"},
            {"IN", "#Cu#/JSRTNNSZPXLcB!#Ew#/3!#F7#1qJUTTNWUPMSPTDQBYEKFQNST]JWGRMZJUHOKZO[HSNRJKJOHKOHIKMRSOITKORQNSJPQYIQOTKPOJJQMMKIKOLSFTMYKQLJ?GJOKSIIMSRSNTJPNHESIKMRRVMSIMIFUOKMRJMITLOLLQTIJISGPQQLQQV?OMTKQCUIPGKHQFJMOHJKNJSJMKPHRN!#Cu#/J"},
            {"IA", "#=9#-u!#?3#-s!#A(#-mP[VTSTH[RdRQS]WT^RSUQX[WSTNSRS`XQZMbGVN]GVGPKTAQBSL`[]N_GZN]@UKVRSOYLPHLNKMOJGKOLI!#?&#0q!#=h#0qPMLKIHRKNGRMNGLLNARMJONERPOLKMPDJMPLLLMRMKMBREIFQIIMOKLNOIJJNJQNIDRFNLHKPLKFIJOJUGOHRLNGSNPJLEJONHSNOHJKOIXO"},
            {"KS", "#8y#1>!#;[#1I!#>-#1L[ZUPQLUPNQTSKRPRRMNTJMNTKQMWLPOR[^RNOXRRQTUSSMPRTO!#>a#4P!#;p#4O!#8i#4D!#8y#1>"},
            {"KY", "#C(#4AKKPIZBSNYRcYUNQHHCQESKSQTJ^LPKJJLGXBUQQKLHQLUSYKPVTNPJLKQKUUSKTO`WRTSMQEXJRKSOSUQQTMOTSNOJUMMETNPKLMSOUJKOQLUSOVVSTOTSPLVKOCSDVOTIQDWLTGJBPKXMYS]EZMNJQKINPGJJYEZTWJRPPSXSXaYQUMXQVWWQRIULZRUTRLVNVEXKU_WPXVTbMRPUMPZ^SPMTURPSUSTYSPYWYO8m7aPUEWOWBTLZ;XHQDX!#E}#4J!#E3#4OKQMM!#D,#4YOM?OS[NS!#Bq#4oRDRNTURNSFLJQKRNKLS@MN~#Bm#4pKOLJQMSOPV"},
            {"LA", "#Cj#;AUYNYMUQCNIKG~#CE#;CZEMUSSKSNLHONN~#A)#;zVKUOZUKRORIP@HPM~#?7#8T!#A^#8MKVTRPGSPPVJTNXWPHWRTTMQKQQIVVTOPGLNSVSQMQRMPPTVQMRKONWMMDSPUWOOJSQHYMMLPVSGTOSIVJOMWWNEUOWMPILNRRRSNQQFSS_LNMJLRUWNRFPORSTMSTXIS!#B|#:JIeNYQQQVYXOSTUNTQTSSSOHQJWLPPRGOLRQUTORMOSSSVMPEUJPLXRJROQTQPSSNRHSQOSKOLSUNNRERUSRMMTHLLTOWMOMILONVEPTRPQHLRSLPSUUORRQVYMTRPLUVRKX^TLQRIQNRTOMSLMLUPTHMLJC^WBPELMMVKOHHELLL?M2gNLLMPLKGKLKVP"
                    + "IKKLTFNPTG[MNISNTJNPKFHEOLMCLJKSNQJTTQMOTSPNGHKOKLOITOKMPRJLLGSLILPMEGPQEFODRQHPPOKGPLNORDUMLJQRSTPMQQTUMLRQPFPBT8K!#@'#;m8P:RGSIDTKTCUIMLRJMIOGKLOJTJKFTJOKSJOKTGNIRJLJSML@KPMFJLPJLHLMPKHKMKRGMGHDEF!#?7#8T"},
            {"ME", "#Q/#*FOHVTMSJM~#Po#*RUFUXNSKQLNNILP~#Pj#*FPCROO[KO~#Ou#+uEKNGCGKHNC!#O'#)jSLNMVPQURPPGLGSKRRTNMJIJV?WFLKVDOJJNPLLMQCKLQAVEK6!#Od#'AZOT`YRXGSNRI[KNKQMZN`WVVUP!#Q,#(QRQLSTSMRRZTNONUSYOQLTWJRVVPTMUXXTQPHXO]aSPRXGcJRKHJTSRMSLONTJPTTLTHIKTLKM]KOLTMNNVKNNLKQQTMURQHRIOIHTILLNRMLHUQVSSLZORSSOTLQLDJMMKQJHKLQLGQKLJLWLMKSTULcPVSNLRSRNSHQNULPPTLPOHMNLTOIKQPSLWLLOSMRKNPTLLMWJTLIKPLVLOMTLOLTRUMRLNJSNSRSLUQQHSNd"
                    + "LW"},
            {"MD", "#M6#1tLPNHSOOU~#M,#1fNIRMTZHL~#J6#0]!#L}#0+!#MA#1A!#Mo#18O[Jm5XMSKNKPJUMONESLJNQIJPMRMHVLIKQFKQNXKKQLLKMVQQNSIQGIDATMLIOKRMQQQLPRYOJKGMIJOQKNPTMNNGRGTRPMLGMNLSLOPSLQQ=QTUOQLLKQJKPPVMNNIKKS=RJWNLNRILOPEGRNSTSFZKGP]LHKOSVKSSPIVDKTUWSPVSRJSQUMONTQQLVRRSb[ZNUKOQRRMRPNRVYQXGJMKMPOSEG@PIERXIMFBCZLMKGPHUFNLUINIVDDGKVLMHNNLHLGPJLQDFJBONGEHNKRMNMCQGIJOJWINMTLOPRMSHPIOFKQMKNORG`ENEbCZ!#J6#0]"},
            {"MA", "#Pa#-RYNSLPIUVLTIPCL~#P:#-XSNRGTIYUQKPV?TKTJK~#N1#,a!#OW#,DQJSNMKRKTOPJUKVNV_UQQKSQMWMNDWRQH[SSKQQTROPLRNRRUOSRWXOVLPTSWORYUS_O]DPMJEMRKDKNLQKNRMYPVVUZSYOVMTOGLNAWIPITMUFRGZEU`>LCQMHMPTLOPTLNORIQOYDTKAJNIIJNLELPLD4WON!#Nb#-<OSLPOK#YLL!#N1#,a"},
            {"MI", "#ES#+&ROQR_RLTIODD~#Ds#+ER@SOQ]KSJM~#D[#+sPMTPPVILNL~#DI#/A[BV9VBT:P9M=H<F?C2T?H9SIX:P:LHPJXKN@UNQITQSNX8TMPRJTSUJWRMPUMXQTQORDMNQHQMO]KXROV>M0XF[MVLNLGOLKLGY@JL]OQKeYWMVQVTPT]PWTTOWSVNWVMQ[cFLLSQVSSTPT_NZQgIVJOMTO`IQOSFQJVN_SVSQQLQRWSVCTLMLRDPSTIJNUNRI]JPLVL[TX^ZrScYfMTP_N^EYOJLMTHJMITNRRTLOMSPVMXFSLWP^LQOUD^LWQSMP!#Ew#/9OI!#DI#/A~#B3#)ASIfBWG^IAb<ZTPCUKOKL~#AL#*bhD^AgKYIWFWNTFc@WC^FhLTQPR@RPS?^O"
                    + "TKQKYKTM]QU[?UKH]UHaMaVY^VTQVaOTKXVSOQLRNRSMERMQRNSSQVFcBRP]KiM]GcKKVQ^MQRSTMXRYJRUWLQIYNQLUNSkHPNTWNVRLSUTWQPLSROJLJZNSPSUSQMTLQIMHQHM@PBSLQHKLQLIINMRPTLRRWMQ=A9JCPF]GNJQNSFLASMRMZLONQJQNSLPORMPPTILNLRMQGROQHNLMNKVFMPTLWIRLNNFMMLQPYHSHf?mQRJNJJS?LKKSJOLMSCMJQOPKKKRMMLHKLOLLIPLMSJMJELLPJLMQIKHPORGLMP:E!#Aa#*vG@JNNLKQML"},
            {"MN", "#<h#(D!#>0#(DO-UQUNWUXuNYRRVR`ORTgPRYdLOKVLWMQQ[N`VTOOSLONRRQXNYdULLIRL[NSROTRR_QNRUUSNOUcJf@ROT[ZMQQjLUQSUUQVKZQTM3c3Y=Z6i=f*pTWJKIRNUJN!#@3#+9KUJOMSLNCXK[JSN]VOWYOUIYP`LRR]Lab`]OXZYRXTR[d_YR]cN_SY!#?3#-s!#=9#-u!#=:#,*MJCJD=PK[GSHQ7L9KKKEK>Q;LH!#=##)j!#<l#)5QEM6SCG3"},
            {"MS", "#A^#8MQJIKQLVNNHRKNMLTLIOKUGLMNUKPLLTMOKLMOSJQSIKIVLOMHJTLTSSNMKKPMMOKZMKMMJSJIJTMPRRMMLVNQJILNMRLURPJJPKLQLTROJVKMJUNOSQOOJTLNHRJKCRMPTUKNMIONLRIPUROPMLCROQS[HOKJK!#Cz#6=SUTR!#C}#9K!#D3#:|GTLMLQMLDQILOQGM2YPKLMKQRSITMVEPIFPJJIOKEFMHMMPEU:!#AA#:RUKJFQKKJOLXNPLIGRLQTRPK@XKMMKPLLPLURQNOGYIGPQGTOUHOKWJHKRNQQVEKMOTGONIZKQQPGSOQLHMNJQNMLMQHKPKWRONHJUHMMMSJQLJVGGNPFTJNHKNNWJLSH"},
            {"MO", "#=h#0q!#?&#0q!#@n#0hRUSOPSUSPSVRJ]OYPWUYMTUVOU]ZRTSPSWg`S_MRS[UTRNUF^SYUNSJWRVNSEdNYQUcaVSTOLSQRRPSL^YOQZUOTRRLQPSW]NSKPMTTROTVYOSVSPLLKROUVUPK_SRLPMSRTKXLPJILPJ_MLNHKOMQSVNSQTNSIOPRTRLRGNNQVTQSITNV!#B.#5GY=YFPGMKJNNH!#@T#4{!#>b#5$!#>`#2?JONLKQIKMJLLOFLPCAOLRNQGSMPJTQPJLQNLSLJKPMINMRIN@AKOOFJJPKEGIAOLQNOJMSJOOI"},
            {"MT", "#09#&z!#1k#';!#4+#'_!#5x#'v!#84#(0!#7q#,0!#5c#+w!#2u#+S!#2n#,)KLNKJJPKJEKLMSKNOSKTQTMPCKFRMJ?PEIIRLVJKMPBIKPJTPSMQFDPILGQNOILFIIHROKIJNGSOPFKKPMKMOIHBQIMIQIKNRHEDCZHOMSJRIDJNOHTMOJLJUITPQLNJQNKJOJRKKLPKTPOFQORESKNIROTCMMHQFLNKPMKKJROHJKLJQJLILONGGDLEDHNKEDVOJETLMIPLJIOIF>!#09#&z"},
            {"NE", "#7a#.1!#9F#.=!#;g#.FQSlbUNTGRNZRhMTQPSTRYPPSSNVQORVRNRVYTNQRVOPRLXUZMPPTTTOURPOSUQMUUXLYQ]QSQLRRNRTQNZSQORLNPYTOLQP^RRPWLQPWLSXZNWTOQKOTMPORU^YWNSTTOXVR!#;[#1I!#8y#1>!#9$#0=!#7Q#03!#7a#.1"},
            {"NV", "#+v#-4!#.S#-m!#08#.1!#/-#3zE^INJJLGFNOMJNASN]SVJTN_QSKYR`N_NRIQRRKU!#+0#0/!#+v#-4"},
            {"NH", "#No#*/MJRIPHKMSNSGTSUNQI!#OX#+MP[SV[WPWYSLbHPISNTJOLSQSKPMT!#Nf#,THJMHNHTILKPIMMO@J7RLNFTGNGREMLSEH@PJ]HOMUJUELKQMNIHFS?KIQL"},
            {"NJ", "#M4#00U>TIZJOHg6=CLJIOLCLNIOLEOGROREHEWDNNTGQDVG!#N-#.QLnNRGSM]PRTPSMYQOKR]!#N:#/iGmJ[C]HjLTGQQ:ENNNIPNRKJGM@DJNNDKK"},
            {"NM", "#36#3l!#5L#4*!#8##4>!#7z#4mMO!#7V#9D!#4U#9+LXRVTS!#34#90Jy!#2H#9P!#36#3l"},
            {"NY", "#N)#.sQSKWGTQBWK~#IU#..c=TI[FSBZDGAJMNHJNPIKEt>qI_O`VXIlJYId7WORLQKI;IMQJRNPQSKNLJLJQOLHMP@SMSKOK^DTHPGk)^AXJUO!#MU#*LRWNWRQQdUVRUNURWLWO^W^OTRSObQPOJTMTW!#N.#,[RT!#N/#-DRR!#N>#.1UUA^VXJWPULPRSRGSOPLWKOLUPOL[QTKOLqE_<VLKVTRTOPMTSWHOMUMOQ1i(m/`AQEVOLIPKFR1!#MK#.=KIDQMLKOFFQMKCLNOKHKJQLIIK!#K$#./!#IY#.FK7"},
            {"NC", "#Mq#3qTOTXKOOKIJ~#Mm#4iPJn:L1H@X_Qq?X?^~#I%#4)!#J.#3x!#Jg#3o!#Lk#3K!#MZ#3;]kjt@?LOJBEBMFJIIPNLLQVVRNPT^hILIELOQTSQGODGLOZ[GSGKWTMQ<JVTUOHSIWJPLMJOJGPCNNLVPUYbYLWISS[GWNSQNYTaQNM:RJMNVLTQUZS`LTGRJYNULPORKT6QJLJGWMOKERMRUZEMLP:KUUTNbT[NSTIdB[@IQTaSWGXLPIROSVTNLJUPQUKVKQJbJNNILNPVKP[RUBc/RP;nHdLJEL6WCW8hF^JZKmKONMCO@SDV!#Jw#5Y!#J$#5fNDABIUMF!#Hh#5SGQNMLTLO;]NM8Z!#G.#5{O:TJPQVNTJNEXCVJeLYERORHSNPLULUO"
                    + "UFNHRNRQQHYGRRNUSPVIRGVIROQMTOQRVMU=WIUPLIQINJPI"},
            {"ND", "#84#(0!#:Z#(>!#<h#(DWlK[QiMY!#=##)j!#=$#*GRVMdSaSYSSRfN^!#:_#+D!#7x#+4!#84#(0"},
            {"OH", "#Ew#/9!#Fw#/*PQTPRNVS[RXUTKPKSSSNPRWVXRn>TP]Nk2!#I(#.Q!#I;#/qHRMSVXQ^IgP_LPPSLZRQMXKQ?hGTKOKJHVPVHOJYQVKQSVPVKNKVLMPKLKJLJULVPUJSU`NQHRN[BUJNMKFHGNI@FSHYHPLRIJDLIRLUGMHGFMIQEMF>FKNKLNGTDJJS!#Ew#/9"},
            {"OK", "#8##4>!#9?#4F!#;0#4M!#=3#4Q!#>a#4P!#>b#5$!#>q#6/!#>p#7wLQJIFNJIIQJKKHIOIHHMKW;NNKCSLNPSHPHKNQLOMQIOIROSLQJMJVIGJOMKGLRLIMMTJQMKJQMMMHIOOUHVNVINMIRLLHKSIOLSKOMMOJEMOLHLA[GJRGDMMHQKMMKRAKKVJPHGJNGR>FJQEMMDCCNWAJNSGNA?LMKP!#:D#4z!#7z#4m!#8##4>"},
            {"OR", "#*Y#(BSOQTNSRS`PY]R[JkSTaXRR_PdITR]OVUSOVTOUSORM[RbKTPQTUP]PRM^LcQVKTR_OWRYK!#/%#)iUbSPPSVSOVRU<gH`EXNWF[FRE`JRLWQULRRSSNRTTPNRRPPSHTQUEb!#.(#-c!#,p#-I!#+2#,}!#(t#,AKHMIP:RFUDPGMHMMOFZC[8NLTL]?Y;!#)k#)kS@b)T=VAPHNLRIPETKP;WWOLVOQR[L"},
            {"PA", "#I(#.Qf>PJSQb@Sh!#K$#./!#Ln#-iUSRUTMVSOSRPS[MQXXSOQRZMSUHWMZJWPPGZVYLYLOOWRYUORPR[UORTb[8iOVDTJUDNIRHY!#Kf#0C!#II#0j!#I(#.Q"},
            {"RI", "#Ov#-BTPS^KSJAMPS[KPORKOQ?SI~#On#-YNFPOQUMR~#Og#-xPHRVKO~#OR#-nPLMJRM!#OF#-/kGRZRNRYWTPRLSMIGJQXKORQRULQSXMY8Z"},
            {"SC", "#H,#5ogDPQdBRORJPQWM!#Ih#5JQXUI^]PZ!#Jw#5Y!#L$#6FCVAaDjOcFYNVFRLLKRMURPPQ=aKMMSSPOSHXDSMSMNGXLMLRMMLPNRUUMVCVNLIMLSWTLUFVLTPPLOHKKPKNNHKKQJFCPLGHHNKKPMLKNBGGOI9DNJLPKLNJKNKLQGCFCJIDGJ>GKHDCLGHGKFINKQHKHHJOGJMKPISLPIWHOJ"},
            {"SD", "#7p#,0!#7x#+4!#:_#+D!#=3#+KNYKVCWNSZb[TQT!#=9#-uFOOUTSOVKPPVTORYNTKPPWLROVIWOTUTSXNRRRLPMLJPHEPLHLOLHMKPNKENJLNKJM7QDLLPJWIP3=MK!#9F#.=!#7a#.1!#7p#,0"},
            {"TN", "#B^#5CQOPHUJMKHJPMWPRLJLNLUOPKMJPKNMSOQRQKXN!#Cz#4hPKKC`OOQ!#E-#4OQQSM!#Fg#4D!#GI#4>PM!#Hn#4+OMdLLXPTMURUINGUIbHQMLJOMQLOHULWHUKNPILLEWMVLMLPPVIXIOIRNRKPLVLOEY:RHTF[PYJTHPNMJTOe!#E)#64!#B8#6HPKVPQFQNRPRMJKRLMLKNNLSNQGLMNRLOOKSKROQUQIMKOKSNTSOLLKVKNKKQLLOJYMQKUMJLOLXMHF"},
            {"TX", "#7z#4m!#:D#4z!#:=#6wSNRQ^`WPPK^TPG[[QZYQTMaXWLTPVWTNSH^SSLQQMSQVZQLWWT^CSORRORYQOTUQRKUOSKRVLRQUUPPHVHOIUOQVQQTMQSTMQJUQLRWRQSTOUWTHTQRMOKULUOQMROPMVSVNNKRP[KPSdPSGVQUVUOSVTSUMTUXPTUTLOSUQPRTLTQRLTRQMSQ!#?9#9WYXVZQWLWQSVSNSRQRVNTTRQXSNR_KQRTLTPUJWOSKTOSJTSXJTOTSROWQULTQRIUJ[JSU]CODR(bGWLPWDUNQKUPKJ<SUGPHOJJMJQKWKPLKKVQQMSRSTQNSRPO[JPDZJNNVQQZF^DPJVQ7a3jKP!#=?#=4DZ2b>bDbBjJaM`Qh[|RdE%HNTMH3O;S9TA_5"
                    + "NLSHTLZ?TMRKSOZF]KNHAXJOMGJOLYQRNRDXHMNKAXURXJOUAdLOMJ<NMRRRWMNTQSXSBrGSLNSMPKLNFZVPYMIxRbQ[ROWcLSQZVQPRRNPXDPGSOUCJDCFNLL<NNRLLIQGLHIOMKOIILPHKHQCBIPCJLPLLQKIIL>MMMFFGOKLKMOP:GIRMRBKNPIHGLQMLLOJHGHK?JMHBFJHFJ=QMKNOIKLOLKML@MOMHMOK<IIMH?CKGHMNKHMPLNIMUMGKNLFJMKPNLLQEODK:OJJCLKXGMKQCQJZMONSLQK_LOLXRPKTIPMSHUMUBOKKHLEDHNILKH@KCFFBINFFJINFG@NCQDKCHGL;KNMHKNKJHIKOEHOLDELFCFIDJLNJ=DD5JLLPGGLHRF!#7V#9D!"
                    + "#7x#4mQO"},
            {"UT", "#08#.1!#2J#.S!#2;#/T!#3h#/i!#36#3l!#1B#3Q!#/;#30!#08#.1"},
            {"VT", "#MU#*L!#No#*/MRSUK`VXPUMQRSIYITOQBVNTV_KYQRLYPWJWPXLRThO_QQNURSJUPVQVVT!#N1#,aLJ!#M|#+uJGJQOTMNO=LKOJGAOARGLGPILIIHM;LMPGLG"},
            {"VA", "#MM#1oQKjFJ_NRJNMSLYM[QSKYQPLZLTOXLVJQNRNIIGO>Q=SANMTLQJMKLPTI~#H^#30SQJSTRPTVVTOXUWNXGRIZVfERKLLPKVS^CRNRTZEPMLONLVHIJSD[<RDNMW@MLVBMLQDWQUW[QUFX,YUSCSJORUGPPRIMNXAMMS@LDyhT<YOXTMZTRWNVRPRVPVSRSQ[IUPRKRLMLbTYWLVIRQOUUSORYRTNSQPMUNXYfWOTKRRZMSSPJRGLLQHEFLBDKMOQ_^XRXYUNQQXPKSRQRMRTP]DIMRXVESQRTMPUSMSRRSNWLNKUJJCIOKJJHSFKJSSQVMTSQOPJS[_VPTVMQMRQMISN]QULap!#KS#3b!#J.#3x!#I%#4)QL;ROQ!#GJ#4<YHVMdFRD]JO"
                    + "GYGNIh>g2"},
            {"WA", "#+X#'NVCPTMTSQIRKOOL~#+`#&YZBROOTRULPMMFTTPQSM_TITVOWLSLMOHJOPMLKQDLNKG~#+O#&2QMSPQUSQMHYJXVJROTJ[KNMJHMKKME~#/Y#&n!#/##)HSTP[KUPX!#-Z#)JESGL@OJLHS<MARLQBNINMJJN=SCLLQKOOIHJKOHIBOJL;U@NLL>FKJT4LCEB?NLKPKMJDLLKERHFJRY/QTJbRPQDQRSKNEYJLKGQKJP@RQNQ`MGHOLJLMPNVKPU9PEKGS+MMPJIGNGRDNHWBMISPTSWYWT]]`SUTXQQRWRULUXVPOXQOQLMKPLWOOQKQPSTKOWKOQYQQJONSKPHYSEKPJW=_IWWQWNRMCPKL^AXLUNVFWKPHRSN_KMPSMZKTQQF_PRJTKHR"
                    + "HKQMTOSSSMOKUJASMPJJTOULRROPUTRVJVDSURKVNN9UNKITJPHSLSGUNPJIEPELNLRS]HGQDSORQRMNKJJNGHPNJQKSOSUSPOFSPQMLIOIQMMLFPRLLMOIMKRLKMTJ!#-`#&C!#/Y#&n"},
            {"WV", "#H&#2@TP]IPCVLPMI?TKNIRHTITRRSNSRQSHSPNHKHSMMHTEVONHVHSTSOWJ`7SMQFLMRDNKRNN@U8MAHFQKVL!#II#0j!#J6#0]!#J>#11[DY=YPW?OLSPMQXS]NQKNLROQJUPTGTOWU[MPQLQPSVRS[ROJc&7RZK_QQF^QPLUNNIWOLKTK[EIFsIXCMIGGMMZQRH]QRG_PQLZCcKZUTHVPRRONQDYLJLPA[HKNSRRLS9YDHLUFWGPFIJOHHNJJLTKAMEGKNJEIKNKILQJKNDAQNNIQLLA"},
            {"WI", "#C|#+ePIVOKWKM~#A<#*BVJNTJPNN~#A4#*9VLRSRLPSGTIH~#A4#*OYFRQIRPRGSOL~#@B#*ZTQVNcHcDQQWJSJRPTLUUG]QTJXRPXIPKYWYQQRSMPRTPW_!#BH#+(eYQNWROLVNUSQMTRRNYRQTKTRQUNRRROVSQRLQSSNSMOQTK[RQTOSKRSK`TTTPMZCROUF`OYMSQRTOQJXHV=ZJQQOKV>PHWLOIRKTNOWLNO[KSKWQRLSPQIVGhLeR]GVKeRbKYInT`NVRTQXTTL_R[!#AS#.lICALGJKBLML;VCKJHJM<LLP@LIEBEL;@LCFJELFDBO=?R>LBRLN?UEOIGEHOPBTKSC[FRPQKTOSI!#@1#*_TPPIULTS"},
            {"WY", "#7p#,0!#7Q#03!#6:#0)!#4n#/u!#2;#/T!#2u#+S!#5c#+w!#7p#,0"},
    };

    /** Per-state data. Results are D/R/total votes; demographics are population shares (ACS 2012-16, county-aggregated)
     *  plus hand-assembled religion, union, veteran, gun and LGBTQ estimates (PRRI, BLS, VA, RAND, Gallup). */
    static final class StateDef {
        final String ab, name, region;
        final int ev;
        final double d24, r24, t24, d20, r20, t20, d16, r16, t16, m22;
        final double white, black, hisp, other, asian, nativeAm, u29, o65, nocol, nocolW, rural, income, cvap;
        final double wev, wca, jew, lds, arab, cuban, pr, union, vet, gun, lgbt, urban, close;
        final int countSpeed;
        final double cx, cy;

        StateDef(String ab, String name, String region, int ev, double[] r, double[] d, double[] h, double close, int spd, double cx, double cy) {
            this.ab = ab; this.name = name; this.region = region; this.ev = ev;
            d24 = r[0]; r24 = r[1]; t24 = r[2]; d20 = r[3]; r20 = r[4]; t20 = r[5]; d16 = r[6]; r16 = r[7]; t16 = r[8]; m22 = r[9];
            white = d[0]; black = d[1]; hisp = d[2]; other = d[3]; u29 = d[4]; o65 = d[5]; nocol = d[6]; nocolW = d[7]; rural = d[8]; income = d[9]; cvap = d[10];
            wev = h[0]; wca = h[1]; jew = h[2]; lds = h[3]; arab = h[4]; nativeAm = h[5]; asian = h[6]; cuban = h[7]; pr = h[8];
            union = h[9]; vet = h[10]; gun = h[11]; lgbt = h[12]; urban = h[13];
            this.close = close; countSpeed = spd; this.cx = cx; this.cy = cy;
        }
    }

    static final StateDef[] STATES = {
            new StateDef("AL", "Alabama", "S", 9, new double[]{772412, 1462616, 2256352, 849648, 1441168, 2323304, 718084, 1306925, 2078165, -33}, new double[]{66.19, 26.35, 4.00, 3.46, 39.22, 15.34, 75.86, 72.12, 40.68, 45708.31, 3639505.00}, new double[]{32, 3, 0.2, 0.7, 0.3, 0.7, 1.7, 0.05, 0.15, 13, 9, 55, 6, 15}, 20.0, 1, 671.6, 432.1),
            new StateDef("AK", "Alaska", "W", 3, new double[]{139690, 184407, 324097, 153405, 189892, 391346, 116454, 163387, 318608, -10}, new double[]{61.20, 3.30, 7.00, 28.50, 42.50, 10.40, 71.00, 65.00, 34.00, 74000.00, 530000.00}, new double[]{18, 8, 0.6, 4.5, 0.3, 15, 7.5, 0.03, 0.12, 30, 11, 64, 7, 20}, 25.0, 3, 101.8, 520.9),
            new StateDef("AZ", "Arizona", "W", 11, new double[]{1582860, 1770242, 3389319, 1672143, 1661686, 3387326, 936250, 1021154, 2062810, 3}, new double[]{56.15, 4.01, 30.54, 9.30, 40.79, 15.90, 71.96, 65.64, 9.95, 51693.66, 4613575.00}, new double[]{13, 10, 1.6, 5, 0.6, 4.5, 4, 0.02, 0.04, 9, 9, 46, 8, 30}, 21.0, 3, 208.0, 382.3),
            new StateDef("AR", "Arkansas", "S", 6, new double[]{396905, 759241, 1182676, 423932, 760647, 1219069, 378729, 677904, 1108615, -30}, new double[]{73.37, 15.42, 6.97, 4.23, 40.06, 15.66, 78.28, 76.01, 43.31, 43195.05, 2175330.00}, new double[]{33, 3, 0.2, 0.8, 0.3, 1, 2, 0.03, 0.06, 7, 9, 57, 6, 12}, 20.5, 1, 563.6, 390.9),
            new StateDef("CA", "California", "W", 54, new double[]{9276179, 6081697, 15862536, 11109764, 6005961, 17495906, 7362490, 3916209, 11954317, 18}, new double[]{38.38, 5.58, 38.56, 17.47, 41.44, 12.88, 68.42, 57.97, 4.97, 65366.51, 24582600.00}, new double[]{7, 9, 3, 1.9, 1.2, 0.9, 16.5, 0.02, 0.03, 25, 5, 28, 9, 40}, 23.0, 3, 82.1, 287.9),
            new StateDef("CO", "Colorado", "W", 10, new double[]{1728159, 1377441, 3190873, 1804352, 1364607, 3256953, 1212209, 1137455, 2564185, 17}, new double[]{68.97, 3.87, 21.13, 6.03, 40.47, 12.65, 61.35, 55.49, 13.45, 63927.38, 3824445.00}, new double[]{13, 12, 1.8, 2.1, 0.4, 1.1, 4, 0.02, 0.04, 12, 9, 45, 9, 30}, 21.0, 2, 331.6, 289.8),
            new StateDef("CT", "Connecticut", "NE", 7, new double[]{992053, 736918, 1758429, 1080680, 715291, 1824280, 884432, 668266, 1623542, 15}, new double[]{68.67, 9.69, 14.98, 6.65, 37.42, 15.47, 61.90, 57.01, 11.88, 72706.81, 2582880.00}, new double[]{5, 23, 3, 0.4, 0.6, 0.3, 5, 0.03, 0.55, 26, 6, 23, 8, 25}, 20.0, 2, 885.9, 190.5),
            new StateDef("DE", "Delaware", "NE", 3, new double[]{289758, 214351, 511697, 296268, 200603, 504010, 235581, 185103, 441535, 14}, new double[]{63.50, 21.14, 8.84, 6.52, 38.26, 16.46, 69.44, 66.97, 17.00, 61520.37, 689655.00}, new double[]{11, 14, 1.6, 0.4, 0.5, 0.4, 4.5, 0.04, 0.3, 16, 9, 30, 7, 25}, 20.0, 1, 853.3, 259.9),
            new StateDef("DC", "District of Columbia", "NE", 3, new double[]{294185, 21076, 325869, 317323, 18586, 344356, 260223, 11553, 280272, 75}, new double[]{35.77, 47.43, 10.49, 6.32, 41.60, 11.41, 44.64, 9.19, 0.00, 72935.00, 494090.00}, new double[]{3, 7, 4.5, 0.5, 1, 0.3, 5, 0.05, 0.15, 16, 5, 10, 14, 100}, 20.0, 1, 827.8, 267.3),
            new StateDef("FL", "Florida", "S", 30, new double[]{4683038, 6110125, 10893547, 5297045, 5668731, 11067456, 4485745, 4605515, 9386750, -19}, new double[]{55.58, 15.45, 24.11, 4.86, 35.82, 19.05, 72.03, 66.02, 8.67, 49050.78, 14195900.00}, new double[]{15, 12, 3.4, 0.6, 0.6, 0.3, 3.2, 0.45, 0.3, 10, 9, 38, 8, 25}, 20.0, 1, 767.3, 515.4),
            new StateDef("GA", "Georgia", "S", 16, new double[]{2548017, 2663117, 5250047, 2473633, 2461854, 4997716, 1837300, 2068623, 4029564, -1}, new double[]{54.12, 30.75, 9.18, 5.95, 41.63, 12.34, 70.58, 64.23, 24.25, 52480.77, 7063795.00}, new double[]{23, 5, 1.3, 0.7, 0.6, 0.3, 4.8, 0.06, 0.15, 8, 9, 50, 7, 25}, 19.0, 1, 736.0, 427.9),
            new StateDef("HI", "Hawaii", "W", 4, new double[]{313044, 193661, 516701, 366130, 196864, 574469, 266827, 128815, 428825, 27}, new double[]{22.37, 1.68, 10.03, 65.91, 38.51, 16.10, 68.60, 54.43, 8.12, 72569.94, 1006640.00}, new double[]{5, 6, 0.6, 5, 0.3, 0.3, 48, 0.02, 0.3, 40, 9, 9, 8, 35}, 24.0, 2, 317.0, 587.7),
            new StateDef("ID", "Idaho", "W", 4, new double[]{274972, 605246, 904812, 287021, 554118, 868105, 189677, 407199, 688235, -40}, new double[]{82.90, 0.60, 11.96, 4.54, 42.49, 14.27, 73.72, 72.14, 28.75, 49496.79, 1148900.00}, new double[]{18, 7, 0.3, 23, 0.2, 1.3, 2, 0.02, 0.03, 9, 9, 60, 6, 15}, 23.0, 1, 198.7, 146.5),
            new StateDef("IL", "Illinois", "MW", 19, new double[]{3062863, 2449079, 5592368, 3471915, 2446891, 6038850, 2977498, 2118179, 5374280, 13}, new double[]{62.22, 14.09, 16.62, 7.06, 39.88, 13.88, 66.96, 60.43, 11.33, 60574.05, 9003485.00}, new double[]{10, 17, 2.3, 0.4, 1.2, 0.3, 6.3, 0.02, 0.12, 22, 6, 28, 8, 35}, 21.0, 2, 615.0, 270.1),
            new StateDef("IN", "Indiana", "MW", 11, new double[]{1163603, 1720347, 2933700, 1242495, 1729852, 3033198, 1031953, 1556220, 2722029, -20}, new double[]{80.20, 9.10, 6.52, 4.18, 40.58, 14.27, 75.15, 73.71, 27.21, 51379.55, 4824565.00}, new double[]{23, 10, 0.5, 0.6, 0.5, 0.3, 2.8, 0.02, 0.05, 15, 8, 45, 7, 20}, 19.0, 1, 665.3, 269.1),
            new StateDef("IA", "Iowa", "MW", 6, new double[]{707278, 927019, 1656849, 759061, 897672, 1690871, 650790, 798923, 1542880, -18}, new double[]{86.96, 3.24, 5.56, 4.24, 39.91, 15.80, 72.39, 71.52, 35.33, 55214.44, 2295045.00}, new double[]{17, 18, 0.2, 0.7, 0.4, 0.5, 2.8, 0.02, 0.04, 13, 9, 45, 7, 15}, 22.0, 1, 539.9, 227.2),
            new StateDef("KS", "Kansas", "MW", 6, new double[]{544853, 758802, 1327591, 558669, 758100, 1349567, 414788, 656009, 1147143, -3}, new double[]{76.84, 5.63, 11.31, 6.22, 41.78, 14.34, 68.31, 65.96, 25.24, 55357.44, 2061765.00}, new double[]{21, 13, 0.6, 1, 0.5, 1.2, 3.4, 0.02, 0.04, 12, 9, 46, 7, 18}, 21.0, 1, 456.2, 309.1),
            new StateDef("KY", "Kentucky", "S", 8, new double[]{704043, 1337494, 2073309, 772474, 1326646, 2136768, 628834, 1202942, 1922218, -22}, new double[]{85.37, 7.84, 3.33, 3.47, 39.10, 14.80, 77.14, 75.96, 41.04, 45959.05, 3313780.00}, new double[]{33, 7, 0.3, 0.6, 0.4, 0.3, 1.8, 0.04, 0.06, 14, 9, 54, 6, 15}, 19.0, 1, 688.3, 321.0),
            new StateDef("LA", "Louisiana", "S", 8, new double[]{766870, 1208505, 2006975, 856034, 1255776, 2148062, 779535, 1178004, 2027731, -30}, new double[]{59.29, 31.94, 4.81, 3.95, 41.31, 13.64, 77.02, 70.95, 26.39, 46248.26, 3432025.00}, new double[]{19, 16, 0.3, 0.6, 0.4, 0.6, 1.9, 0.08, 0.2, 8, 8, 52, 7, 20}, 21.0, 1, 576.2, 478.3),
            new StateDef("ME", "Maine", "NE", 4, new double[]{435652, 377977, 831375, 430473, 359899, 813740, 354873, 334838, 741550, 13}, new double[]{93.74, 1.16, 1.49, 3.61, 33.84, 18.22, 70.68, 70.50, 61.11, 51456.53, 1051865.00}, new double[]{9, 18, 0.8, 0.7, 0.4, 0.6, 1.4, 0.02, 0.25, 17, 10, 45, 8, 10}, 20.0, 2, 919.0, 93.5),
            new StateDef("MD", "Maryland", "NE", 10, new double[]{1902577, 1035550, 3015650, 1985023, 976414, 3037030, 1497951, 873646, 2474543, 29}, new double[]{52.49, 29.17, 9.23, 9.11, 38.96, 13.76, 61.62, 53.88, 12.68, 78895.89, 4212250.00}, new double[]{10, 13, 4, 0.7, 1, 0.4, 7.2, 0.05, 0.2, 17, 8, 30, 8, 30}, 20.0, 2, 831.4, 263.3),
            new StateDef("MA", "Massachusetts", "NE", 11, new double[]{2126545, 1251308, 3453437, 2382202, 1167202, 3631402, 1964768, 1083069, 3231531, 29}, new double[]{73.72, 6.57, 10.85, 8.86, 38.14, 15.08, 58.84, 55.11, 7.92, 72391.21, 4887320.00}, new double[]{4, 26, 4, 0.4, 0.8, 0.3, 7.6, 0.04, 0.4, 21, 6, 15, 10, 30}, 20.0, 1, 896.9, 172.3),
            new StateDef("MI", "Michigan", "MW", 15, new double[]{2736533, 2816636, 5662504, 2804040, 2649852, 5539302, 2268193, 2279805, 4790917, 11}, new double[]{75.72, 13.74, 4.79, 5.75, 38.79, 15.42, 72.42, 70.76, 25.31, 51729.19, 7408635.00}, new double[]{16, 15, 1, 0.4, 3, 0.7, 3.8, 0.03, 0.08, 23, 8, 40, 7, 25}, 21.0, 2, 684.1, 184.8),
            new StateDef("MN", "Minnesota", "MW", 10, new double[]{1656979, 1519032, 3240916, 1717077, 1484065, 3277171, 1366676, 1322891, 2916404, 6}, new double[]{81.32, 5.60, 5.06, 8.02, 39.52, 14.30, 65.74, 63.37, 26.19, 64828.63, 3980480.00}, new double[]{12, 19, 1, 0.4, 1.5, 1.2, 5.6, 0.02, 0.05, 23, 8, 40, 8, 25}, 21.0, 2, 524.7, 131.9),
            new StateDef("MS", "Mississippi", "S", 6, new double[]{466668, 747744, 1228008, 539398, 756764, 1315178, 462001, 678457, 1162987, -26}, new double[]{57.21, 37.38, 2.90, 2.51, 41.34, 14.27, 78.80, 74.00, 50.20, 41085.15, 2217760.00}, new double[]{30, 4, 0.1, 0.4, 0.3, 0.6, 1.1, 0.04, 0.1, 9, 9, 55, 6, 10}, 20.0, 1, 618.1, 437.5),
            new StateDef("MO", "Missouri", "MW", 10, new double[]{1200599, 1751986, 2993596, 1253014, 1718736, 3025962, 1054889, 1585753, 2775098, -14}, new double[]{80.05, 11.50, 3.92, 4.54, 39.42, 15.35, 72.27, 69.71, 29.28, 50748.27, 4547870.00}, new double[]{23, 13, 0.8, 1, 0.6, 0.5, 2.3, 0.02, 0.06, 15, 9, 50, 7, 20}, 20.0, 1, 560.4, 311.6),
            new StateDef("MT", "Montana", "W", 4, new double[]{231906, 352079, 602963, 244786, 343602, 605750, 174521, 274120, 483574, -15}, new double[]{86.83, 0.39, 3.40, 9.38, 37.98, 16.70, 69.91, 68.72, 43.83, 48641.95, 789490.00}, new double[]{18, 13, 0.3, 4, 0.2, 6.5, 1, 0.02, 0.05, 17, 11, 66, 6, 10}, 22.0, 2, 286.9, 99.6),
            new StateDef("NE", "Nebraska", "MW", 5, new double[]{369995, 564816, 947159, 374583, 556846, 956379, 273858, 485819, 805638, -24}, new double[]{80.34, 4.57, 10.20, 4.90, 41.56, 14.43, 69.78, 67.38, 26.02, 55183.66, 1341015.00}, new double[]{16, 20, 0.4, 1.3, 0.6, 1, 2.8, 0.02, 0.04, 10, 9, 45, 6, 20}, 21.0, 1, 434.0, 238.7),
            new StateDef("NV", "Nevada", "W", 6, new double[]{705197, 751205, 1484840, 703486, 669890, 1405376, 537753, 511319, 1122990, 0}, new double[]{51.27, 8.22, 27.84, 12.67, 39.53, 14.12, 76.81, 72.22, 5.62, 53275.58, 1902290.00}, new double[]{9, 10, 2.5, 6, 0.6, 1.7, 10, 0.06, 0.08, 20, 9, 37, 9, 45}, 22.0, 3, 142.2, 251.8),
            new StateDef("NH", "New Hampshire", "NE", 4, new double[]{418496, 395578, 822153, 424921, 365654, 804430, 348521, 345789, 731553, 3}, new double[]{91.24, 1.17, 3.28, 4.31, 35.69, 15.85, 64.49, 64.50, 39.53, 69517.38, 1027635.00}, new double[]{7, 22, 0.8, 0.7, 0.4, 0.3, 3, 0.02, 0.2, 12, 9, 40, 9, 10}, 19.5, 1, 892.7, 140.2),
            new StateDef("NJ", "New Jersey", "NE", 14, new double[]{2220713, 1968215, 4272725, 2608335, 1883274, 4564234, 2021756, 1535513, 3674893, 3}, new double[]{56.69, 12.72, 19.29, 11.30, 37.83, 14.72, 62.62, 57.40, 5.21, 74686.16, 6087275.00}, new double[]{5, 21, 6, 0.3, 1.5, 0.3, 10.5, 0.07, 0.3, 26, 5, 15, 8, 30}, 20.0, 2, 861.6, 230.2),
            new StateDef("NM", "New Mexico", "W", 5, new double[]{478802, 423391, 923403, 501614, 401894, 923965, 380724, 315875, 783127, 12}, new double[]{38.68, 1.79, 47.82, 11.71, 40.76, 15.28, 73.66, 60.87, 22.30, 46241.30, 1464245.00}, new double[]{9, 9, 0.6, 2, 0.4, 10, 2, 0.01, 0.02, 9, 10, 46, 8, 25}, 21.0, 2, 310.5, 393.2),
            new StateDef("NY", "New York", "NE", 28, new double[]{4619195, 3578899, 8198094, 5244006, 3250230, 8616205, 4143874, 2640570, 7046175, 6}, new double[]{56.41, 14.37, 18.59, 10.63, 38.89, 14.69, 65.54, 56.88, 11.83, 64021.34, 13605855.00}, new double[]{5, 18, 9, 0.3, 1.5, 0.4, 9.5, 0.05, 0.45, 33, 5, 20, 8, 45}, 21.0, 2, 833.9, 171.7),
            new StateDef("NC", "North Carolina", "S", 16, new double[]{2715375, 2898423, 5679647, 2684292, 2758773, 5524801, 2162074, 2339603, 4629471, -3}, new double[]{63.99, 21.17, 8.90, 5.94, 39.34, 14.68, 70.80, 66.03, 33.10, 49484.63, 7203800.00}, new double[]{25, 6, 0.5, 0.8, 0.6, 1.3, 3.5, 0.05, 0.2, 5, 9, 45, 7, 25}, 19.5, 1, 800.9, 350.6),
            new StateDef("ND", "North Dakota", "MW", 3, new double[]{112327, 246505, 365059, 114902, 235595, 361819, 93526, 216133, 336968, -40}, new double[]{86.37, 1.97, 3.12, 8.54, 42.80, 14.23, 71.58, 70.70, 39.45, 59707.09, 555685.00}, new double[]{15, 22, 0.1, 0.6, 0.3, 5.5, 1.7, 0.02, 0.04, 9, 10, 55, 6, 10}, 21.0, 1, 429.1, 104.0),
            new StateDef("OH", "Ohio", "MW", 17, new double[]{2533699, 3180116, 5765017, 2679165, 3154834, 5922202, 2317001, 2771984, 5325395, -13}, new double[]{79.96, 12.10, 3.46, 4.47, 38.74, 15.50, 73.22, 71.27, 21.92, 51449.18, 8734140.00}, new double[]{18, 15, 1.3, 0.4, 0.8, 0.3, 2.7, 0.03, 0.12, 20, 9, 40, 7, 25}, 19.5, 1, 723.8, 252.9),
            new StateDef("OK", "Oklahoma", "S", 7, new double[]{499599, 1036213, 1566173, 503890, 1020280, 1560699, 419788, 947934, 1451056, -20}, new double[]{66.93, 7.14, 9.84, 16.09, 41.52, 14.50, 75.37, 72.68, 33.18, 48501.17, 2787660.00}, new double[]{32, 6, 0.2, 0.8, 0.4, 8, 2.6, 0.02, 0.05, 9, 9, 52, 6, 20}, 20.0, 1, 470.8, 376.1),
            new StateDef("OR", "Oregon", "W", 8, new double[]{1228410, 910702, 2207592, 1340383, 958448, 2374321, 934631, 742506, 1808575, 5}, new double[]{77.00, 1.77, 12.43, 8.80, 37.63, 15.93, 68.54, 66.44, 18.70, 54625.58, 2911325.00}, new double[]{12, 9, 1, 3, 0.4, 1.8, 5.2, 0.02, 0.04, 21, 9, 42, 10, 25}, 23.0, 2, 102.6, 134.0),
            new StateDef("PA", "Pennsylvania", "NE", 19, new double[]{3423042, 3543308, 7034206, 3459923, 3378263, 6925255, 2844705, 2912941, 5970107, 14}, new double[]{77.71, 10.59, 6.60, 5.10, 37.39, 16.69, 70.62, 68.16, 21.14, 56751.76, 9725830.00}, new double[]{13, 21, 2.3, 0.4, 0.8, 0.3, 4, 0.04, 0.45, 20, 8, 40, 7, 25}, 20.0, 3, 805.8, 225.6),
            new StateDef("RI", "Rhode Island", "NE", 4, new double[]{285156, 214406, 510659, 306210, 199837, 516383, 249902, 179421, 450121, 15}, new double[]{74.00, 5.44, 14.07, 6.50, 38.12, 15.79, 67.62, 65.23, 9.24, 58483.28, 780250.00}, new double[]{4, 26, 1.8, 0.4, 0.6, 0.7, 3.8, 0.03, 0.4, 25, 7, 14, 9, 25}, 20.0, 1, 903.9, 184.1),
            new StateDef("SC", "South Carolina", "S", 9, new double[]{1028452, 1483747, 2548140, 1091541, 1385103, 2513329, 849469, 1143611, 2084444, -20}, new double[]{63.86, 27.12, 5.34, 3.68, 39.02, 15.75, 73.49, 68.71, 33.03, 47359.63, 3620835.00}, new double[]{26, 5, 0.5, 0.6, 0.4, 0.5, 2, 0.05, 0.15, 4, 10, 50, 6, 15}, 19.0, 1, 779.3, 392.4),
            new StateDef("SD", "South Dakota", "MW", 3, new double[]{146859, 272081, 428922, 150471, 261043, 422609, 117442, 227701, 370047, -32}, new double[]{84.23, 1.63, 3.43, 10.71, 40.89, 15.33, 72.15, 70.66, 41.98, 52915.99, 618215.00}, new double[]{17, 18, 0.1, 0.6, 0.2, 9, 1.6, 0.02, 0.04, 5, 10, 55, 6, 10}, 21.0, 1, 429.7, 172.2),
            new StateDef("TN", "Tennessee", "S", 11, new double[]{1056265, 1966865, 3063942, 1143711, 1852475, 3053851, 867110, 1517402, 2484691, -33}, new double[]{74.52, 16.68, 5.00, 3.80, 39.04, 15.03, 74.42, 70.91, 33.15, 47969.45, 4876600.00}, new double[]{34, 4, 0.4, 0.7, 0.5, 0.4, 2.1, 0.03, 0.08, 9, 9, 51, 6, 20}, 20.0, 1, 673.6, 361.1),
            new StateDef("TX", "Texas", "S", 40, new double[]{4835250, 6393597, 11380105, 5259126, 5890347, 11317911, 3867816, 4681590, 8903237, -11}, new double[]{43.42, 11.63, 38.63, 6.32, 43.92, 11.49, 71.97, 61.67, 14.89, 55916.85, 17177620.00}, new double[]{16, 7, 0.6, 0.8, 0.8, 0.5, 5.6, 0.03, 0.04, 7, 8, 45, 7, 35}, 20.0, 2, 434.0, 469.2),
            new StateDef("UT", "Utah", "W", 6, new double[]{562566, 883818, 1487951, 560282, 865140, 1488289, 274188, 452086, 984957, -18}, new double[]{79.34, 1.03, 13.50, 6.14, 49.45, 10.02, 68.09, 65.48, 9.29, 63068.94, 1903500.00}, new double[]{5, 4, 0.2, 55, 0.3, 1.1, 3.4, 0.02, 0.04, 9, 7, 47, 7, 20}, 22.0, 2, 226.8, 268.7),
            new StateDef("VT", "Vermont", "NE", 3, new double[]{235791, 119395, 366389, 242820, 112704, 367428, 178179, 95053, 291413, 40}, new double[]{93.44, 1.13, 1.74, 3.70, 35.91, 16.96, 63.53, 63.40, 60.89, 56866.50, 493535.00}, new double[]{6, 18, 1, 0.7, 0.3, 0.4, 1.9, 0.02, 0.15, 18, 8, 50, 10, 10}, 19.0, 1, 873.3, 136.0),
            new StateDef("VA", "Virginia", "S", 13, new double[]{2335395, 2075085, 4482576, 2413568, 1962430, 4460524, 1916845, 1731156, 3844787, 4}, new double[]{63.12, 18.87, 8.73, 9.29, 39.35, 13.78, 62.99, 57.57, 23.96, 72743.83, 5998900.00}, new double[]{17, 8, 1.3, 0.8, 1.2, 0.4, 7.5, 0.05, 0.2, 8, 11, 36, 8, 25}, 19.0, 2, 801.4, 304.7),
            new StateDef("WA", "Washington", "W", 12, new double[]{2245849, 1530923, 3898835, 2369612, 1584651, 4087631, 1610524, 1129120, 2957942, 15}, new double[]{70.38, 3.49, 12.08, 14.05, 39.35, 14.04, 66.72, 64.41, 15.66, 64487.90, 5008030.00}, new double[]{11, 10, 1, 3, 0.6, 1.9, 11, 0.02, 0.04, 28, 9, 42, 10, 30}, 23.0, 3, 124.4, 59.0),
            new StateDef("WV", "West Virginia", "S", 4, new double[]{214309, 533556, 762390, 235984, 545382, 794652, 187457, 486198, 708226, -36}, new double[]{92.42, 3.38, 1.42, 2.78, 35.62, 17.77, 80.21, 80.33, 50.98, 43318.28, 1453210.00}, new double[]{31, 5, 0.1, 0.5, 0.3, 0.2, 0.8, 0.03, 0.08, 16, 10, 58, 6, 10}, 19.5, 1, 766.7, 284.7),
            new StateDef("WI", "Wisconsin", "MW", 10, new double[]{1668229, 1697626, 3415213, 1630673, 1610065, 3297352, 1380823, 1403694, 2937326, 2}, new double[]{82.15, 6.18, 6.45, 5.22, 38.61, 15.21, 71.46, 69.34, 29.61, 55672.93, 4313285.00}, new double[]{13, 22, 0.6, 0.4, 0.6, 1, 3.2, 0.02, 0.12, 13, 8, 44, 7, 20}, 21.0, 2, 594.7, 166.7),
            new StateDef("WY", "Wyoming", "W", 3, new double[]{69527, 192633, 266353, 73491, 193559, 276765, 55949, 174248, 248742, -45}, new double[]{84.36, 1.06, 9.67, 4.91, 40.59, 13.84, 73.91, 72.29, 34.93, 59863.70, 432375.00}, new double[]{17, 12, 0.2, 9, 0.2, 2.6, 1, 0.02, 0.04, 9, 10, 66, 6, 10}, 21.0, 1, 307.8, 195.1),
    };

    /** Maine and Nebraska award electoral votes by congressional district. {parent, ev, 2024 D margin, 2020 D margin, vote share of state} */
    static final String[] CD_NAMES = {"ME-1", "ME-2", "NE-1", "NE-2", "NE-3"};
    static final double[][] CD_DATA = {
            {19, 1, 24.0, 23.1, 0.52},
            {19, 1, -9.1, -7.4, 0.48},
            {27, 1, -12.6, -11.2, 0.33},
            {27, 1, 4.6, 6.5, 0.34},
            {27, 1, -51.0, -53.2, 0.33},
    };

    /** U.S. Senate seats up in 2028 (class III). {state, incumbent, party, 2022 D margin, note} */
    static final String[][] SENATE_2028 = {
            {"AL", "Katie Britt", "R", "-36", ""},
            {"AK", "Lisa Murkowski", "R", "-10", "independent streak"},
            {"AZ", "Mark Kelly", "D", "5", ""},
            {"AR", "John Boozman", "R", "-35", ""},
            {"CA", "Alex Padilla", "D", "22", ""},
            {"CO", "Michael Bennet", "D", "15", "running for governor in 2026"},
            {"CT", "Richard Blumenthal", "D", "15", ""},
            {"FL", "Ashley Moody", "R", "-16", "appointed 2025"},
            {"GA", "Raphael Warnock", "D", "3", ""},
            {"HI", "Brian Schatz", "D", "46", ""},
            {"ID", "Mike Crapo", "R", "-32", ""},
            {"IL", "Tammy Duckworth", "D", "15", ""},
            {"IN", "Todd Young", "R", "-21", ""},
            {"IA", "Chuck Grassley", "R", "-12", "would be 95"},
            {"KS", "Jerry Moran", "R", "-23", ""},
            {"KY", "Rand Paul", "R", "-24", ""},
            {"LA", "John Kennedy", "R", "-43", ""},
            {"MD", "Chris Van Hollen", "D", "32", ""},
            {"MO", "Eric Schmitt", "R", "-13", ""},
            {"NV", "Catherine Cortez Masto", "D", "1", ""},
            {"NH", "Maggie Hassan", "D", "9", ""},
            {"NY", "Chuck Schumer", "D", "14", ""},
            {"NC", "Ted Budd", "R", "-3", ""},
            {"ND", "John Hoeven", "R", "-38", ""},
            {"OH", "Jon Husted", "R", "-6", "appointed 2025"},
            {"OK", "James Lankford", "R", "-32", ""},
            {"OR", "Ron Wyden", "D", "15", ""},
            {"PA", "John Fetterman", "D", "5", ""},
            {"SC", "Tim Scott", "R", "-25", ""},
            {"SD", "John Thune", "R", "-44", ""},
            {"UT", "Mike Lee", "R", "-11", ""},
            {"VT", "Peter Welch", "D", "40", ""},
            {"WA", "Patty Murray", "D", "15", ""},
            {"WI", "Ron Johnson", "R", "-1", "pledged two terms"},
    };

    /** Nielsen designated market areas (TV households in thousands, 2024-25, approximate) and the share of each market's
     *  households in each state. States not fully covered get a synthetic "rest of state" market at load. */
    static final String[] DMA_NAMES = {
            "New York",
            "Los Angeles",
            "Chicago",
            "Dallas-Ft. Worth",
            "Philadelphia",
            "Houston",
            "Atlanta",
            "Washington, DC",
            "Boston (Manchester NH)",
            "San Francisco Bay Area",
            "Phoenix",
            "Seattle-Tacoma",
            "Tampa-St. Petersburg",
            "Detroit",
            "Minneapolis-St. Paul",
            "Denver",
            "Orlando-Daytona Beach",
            "Miami-Ft. Lauderdale",
            "Cleveland-Akron",
            "Sacramento-Stockton",
            "Charlotte",
            "Raleigh-Durham",
            "Portland, OR",
            "St. Louis",
            "Indianapolis",
            "Nashville",
            "Pittsburgh",
            "Salt Lake City",
            "Baltimore",
            "San Diego",
            "San Antonio",
            "Columbus, OH",
            "Kansas City",
            "Hartford-New Haven",
            "Austin",
            "Cincinnati",
            "Greenville-Spartanburg-Asheville",
            "Milwaukee",
            "West Palm Beach",
            "Las Vegas",
            "Jacksonville",
            "Harrisburg-Lancaster-York",
            "Grand Rapids-Kalamazoo",
            "Norfolk-Portsmouth",
            "Birmingham",
            "Greensboro-Winston-Salem",
            "Oklahoma City",
            "Albuquerque-Santa Fe",
            "Louisville",
            "New Orleans",
            "Memphis",
            "Providence-New Bedford",
            "Ft. Myers-Naples",
            "Buffalo",
            "Fresno-Visalia",
            "Richmond-Petersburg",
            "Mobile-Pensacola",
            "Little Rock",
            "Wilkes-Barre-Scranton",
            "Knoxville",
            "Tulsa",
            "Albany-Schenectady",
            "Lexington",
            "Dayton",
            "Tucson",
            "Spokane",
            "Des Moines-Ames",
            "Green Bay-Appleton",
            "Honolulu",
            "Roanoke-Lynchburg",
            "Wichita-Hutchinson",
            "Flint-Saginaw-Bay City",
            "Omaha",
            "Charleston-Huntington, WV",
            "Columbia, SC",
            "Madison",
            "Portland-Auburn, ME",
            "Toledo",
            "Huntsville-Decatur",
            "Chattanooga",
            "Savannah",
            "Charleston, SC",
            "Jackson, MS",
            "Reno",
            "Boise",
            "Cedar Rapids-Waterloo-Dubuque",
            "Davenport-Rock Island-Moline",
            "Burlington-Plattsburgh",
            "Myrtle Beach-Florence",
            "Greenville-New Bern",
            "Youngstown",
            "Lansing",
            "Traverse City-Cadillac",
            "Augusta-Aiken",
            "Macon",
            "Johnstown-Altoona-State College",
            "Wilmington, NC",
            "Erie",
            "Bangor",
            "Anchorage",
            "Sioux Falls",
            "Fargo-Grand Forks",
            "Montana markets",
    };
    static final double[] DMA_HH = {7452, 5764, 3473, 3137, 3036, 2743, 2646, 2571, 2534, 2507, 2130, 2049, 2027, 1879, 1837, 1817, 1758, 1712, 1497, 1494, 1333, 1317, 1296, 1227, 1149, 1138, 1132, 1128, 1110, 1092, 1036, 1001, 996, 978, 953, 934, 917, 908, 862, 847, 757, 752, 734, 716, 711, 707, 702, 698, 683, 621, 614, 608, 601, 588, 586, 582, 568, 546, 545, 541, 525, 524, 506, 488, 482, 469, 435, 444, 431, 445, 428, 427, 433, 418, 416, 412, 409, 406, 404, 382, 370, 358, 330, 309, 330, 335, 297, 300, 300, 300, 260, 255, 250, 260, 235, 270, 210, 150, 135, 160, 270, 245, 420};
    static final String[] DMA_STATES = {
            "NY:0.72 NJ:0.25 CT:0.03",
            "CA:1.00",
            "IL:0.88 IN:0.12",
            "TX:1.00",
            "PA:0.66 NJ:0.27 DE:0.07",
            "TX:1.00",
            "GA:0.94 AL:0.06",
            "VA:0.48 MD:0.34 DC:0.13 WV:0.05",
            "MA:0.77 NH:0.21 VT:0.02",
            "CA:1.00",
            "AZ:1.00",
            "WA:1.00",
            "FL:1.00",
            "MI:1.00",
            "MN:0.88 WI:0.12",
            "CO:0.94 WY:0.03 NE:0.03",
            "FL:1.00",
            "FL:1.00",
            "OH:1.00",
            "CA:1.00",
            "NC:0.78 SC:0.22",
            "NC:1.00",
            "OR:0.85 WA:0.15",
            "MO:0.80 IL:0.20",
            "IN:1.00",
            "TN:0.95 KY:0.05",
            "PA:0.90 WV:0.07 OH:0.03",
            "UT:0.82 ID:0.08 NV:0.05 WY:0.05",
            "MD:1.00",
            "CA:1.00",
            "TX:1.00",
            "OH:1.00",
            "MO:0.62 KS:0.38",
            "CT:1.00",
            "TX:1.00",
            "OH:0.75 KY:0.20 IN:0.05",
            "SC:0.60 NC:0.37 GA:0.03",
            "WI:1.00",
            "FL:1.00",
            "NV:1.00",
            "FL:0.87 GA:0.13",
            "PA:1.00",
            "MI:1.00",
            "VA:0.90 NC:0.10",
            "AL:1.00",
            "NC:0.93 VA:0.07",
            "OK:1.00",
            "NM:0.90 CO:0.10",
            "KY:0.85 IN:0.15",
            "LA:1.00",
            "TN:0.60 MS:0.25 AR:0.15",
            "RI:0.55 MA:0.45",
            "FL:1.00",
            "NY:1.00",
            "CA:1.00",
            "VA:1.00",
            "AL:0.55 FL:0.45",
            "AR:1.00",
            "PA:1.00",
            "TN:1.00",
            "OK:1.00",
            "NY:0.95 MA:0.05",
            "KY:1.00",
            "OH:1.00",
            "AZ:1.00",
            "WA:0.65 ID:0.35",
            "IA:1.00",
            "WI:0.92 MI:0.08",
            "HI:1.00",
            "VA:0.97 WV:0.03",
            "KS:1.00",
            "MI:1.00",
            "NE:0.70 IA:0.30",
            "WV:0.70 KY:0.20 OH:0.10",
            "SC:1.00",
            "WI:1.00",
            "ME:0.88 NH:0.12",
            "OH:0.85 MI:0.15",
            "AL:0.90 TN:0.10",
            "TN:0.65 GA:0.30 AL:0.05",
            "GA:0.75 SC:0.25",
            "SC:1.00",
            "MS:1.00",
            "NV:0.85 CA:0.15",
            "ID:0.95 OR:0.05",
            "IA:1.00",
            "IA:0.50 IL:0.50",
            "VT:0.55 NY:0.45",
            "SC:0.80 NC:0.20",
            "NC:1.00",
            "OH:0.90 PA:0.10",
            "MI:1.00",
            "MI:1.00",
            "GA:0.60 SC:0.40",
            "GA:1.00",
            "PA:1.00",
            "NC:1.00",
            "PA:1.00",
            "ME:1.00",
            "AK:1.00",
            "SD:0.80 MN:0.10 IA:0.10",
            "ND:0.70 MN:0.30",
            "MT:1.00",
    };
    static final double[] STATE_TVHH = {2000, 260, 2850, 1200, 13600, 2350, 1420, 400, 330, 8800, 4000, 470, 720, 4900, 2650, 1300, 1150, 1800, 1780, 580, 2350, 2750, 4000, 2300, 1110, 2500, 450, 780, 1200, 550, 3500, 830, 7600, 4200, 330, 4800, 1550, 1700, 5200, 420, 2000, 360, 2800, 10700, 1100, 270, 3300, 3000, 720, 2450, 240};

    // =====================================================================
    //  3.  ATTRIBUTES, ISSUES, VOTER CELLS AND GROUPS
    // =====================================================================
    // ---- candidate attributes (0-100) -------------------------------------------------------------------
    static final int A_CHA = 0, A_POD = 1, A_SPE = 2, A_DEB = 3, A_INT = 4, A_DIS = 5, A_RET = 6, A_SMD = 7, A_BIG = 8,
            A_SELF = 9, A_VOL = 10, A_VIR = 11, A_AUT = 12, A_STA = 13, A_SCA = 14, A_EXE = 15, A_FPC = 16, A_XOV = 17,
            A_EST = 18, A_HOM = 19, A_BAS = 20, NA = 21;
    static final String[] ATTR = {"Charisma", "Long-form / podcasts", "Speeches", "Debating", "Intellect & policy depth",
            "Message discipline", "Retail politics", "Small-dollar fundraising", "Big-donor network", "Self-funding",
            "Volunteer energy", "Media virality", "Authenticity", "Stamina", "Scandal baggage", "Executive record",
            "Foreign-policy credibility", "Crossover appeal", "Establishment ties", "Home-state strength", "Base fire"};
    static final String[] ATTR_SHORT = {"CHA", "POD", "SPE", "DEB", "INT", "DIS", "RET", "SMD", "BIG", "SELF", "VOL", "VIR",
            "AUT", "STA", "SCA", "EXE", "FPC", "XOV", "EST", "HOM", "BASE"};
    static final String[] ATTR_DESC = {
            "Presence and likeability on stage and on screen.",
            "How well the candidate does in long, unscripted interviews and podcasts - where young men now get their news.",
            "Set-piece oratory: rallies, conventions, big moments.",
            "Head-to-head debating under pressure.",
            "Command of policy and quickness of mind.",
            "Staying on message; low gaffe risk.",
            "Handshakes, diners and town halls - the Iowa and New Hampshire arts.",
            "Ability to raise from millions of small online donors.",
            "Access to bundlers and high-dollar donors.",
            "Personal fortune that can be spent on the race.",
            "Ability to inspire volunteers to knock doors and make calls.",
            "Ability to go viral and dominate the attention economy.",
            "How genuine the candidate seems. High authenticity blunts flip-flop attacks.",
            "Ability to keep a punishing schedule without mistakes.",
            "Hidden risk of damaging revelations (higher is worse).",
            "Governing record: running a state, a department or a business.",
            "Credibility as a potential commander-in-chief.",
            "Appeal to the other party's voters and independents.",
            "Ties to party leaders, donors and endorsers.",
            "Strength at home - matters most if the home state is competitive.",
            "How strongly the party's activist base sees the candidate as a fighter."};

    // ---- the 18 issues; positions run from -1 (left pole) to +1 (right pole) -----------------------------
    static final int I_CHINA = 0, I_AI = 1, I_COL = 2, I_JOBS = 3, I_TRADE = 4, I_ISRAEL = 5, I_HOUSING = 6, I_IMMIG = 7,
            I_ABORT = 8, I_HEALTH = 9, I_SOCSEC = 10, I_CRIME = 11, I_CLIMATE = 12, I_DEMOC = 13, I_GUNS = 14, I_EDU = 15,
            I_UKRAINE = 16, I_TRANS = 17, NI = 18;
    static final String[] ISSUE = {"China", "AI & data centers", "Cost of living", "Jobs & AI disruption", "Tariffs & trade",
            "Israel, Gaza & Iran", "Housing", "Immigration", "Abortion", "Health care", "Social Security & Medicare",
            "Crime & policing", "Climate & energy", "Democracy & corruption", "Guns", "Education & student debt",
            "Ukraine & Russia", "Transgender issues"};
    static final String[][] ISSUE_POLES = {
            {"De-escalate and trade", "Confront and decouple"},
            {"Regulate AI, pause data centers", "Accelerate AI, build data centers"},
            {"Government action on prices", "Cut spending and deregulate"},
            {"Safety net, worker protections", "Markets and retraining only"},
            {"Free trade", "Tariffs and protection"},
            {"Condition or cut aid to Israel", "Unconditional support for Israel"},
            {"Rent limits and public housing", "Build, build, build: deregulate"},
            {"Path to citizenship", "Restrict and deport"},
            {"Abortion rights", "Restrict abortion"},
            {"Single-payer / public option", "Market-based, repeal mandates"},
            {"Expand benefits", "Trim and reform benefits"},
            {"Reform policing", "Tough on crime"},
            {"Clean-energy transition", "Drill and burn"},
            {"Limit presidential power, ethics reform", "Strong presidency, loyalty"},
            {"Gun safety laws", "Gun rights"},
            {"Debt relief, free college", "School choice, no bailouts"},
            {"Back Ukraine to win", "Make a deal with Russia"},
            {"Protect trans rights", "Restrict (sports, youth care)"}};
    /** Base national salience of each issue (share of the vote decision driven by it). */
    static final double[] ISSUE_SAL = {0.035, 0.045, 0.20, 0.07, 0.045, 0.025, 0.055, 0.09, 0.06, 0.08, 0.06, 0.045,
            0.04, 0.07, 0.03, 0.03, 0.02, 0.025};
    /** Where the typical Democratic and Republican voter stands (2026 polling). */
    static final double[] IDEAL_D = {0.10, -0.40, -0.70, -0.55, -0.30, -0.45, -0.30, -0.45, -0.80, -0.65, -0.70, -0.25, -0.70, -0.85, -0.70, -0.55, -0.60, -0.40};
    static final double[] IDEAL_R = {0.60, 0.05, 0.30, 0.25, 0.45, 0.60, 0.40, 0.75, 0.45, 0.40, -0.35, 0.75, 0.60, 0.35, 0.75, 0.60, 0.30, 0.80};

    // ---- voter blocs (16) x age (3) x sex (2) = 96 cells per state ---------------------------------------
    static final int B_WEV_NC = 0, B_WEV_C = 1, B_WCA_NC = 2, B_WCA_C = 3, B_WOT_NC = 4, B_WOT_C = 5, B_JEW = 6, B_LDS = 7,
            B_BLK = 8, B_HMX = 9, B_HCU = 10, B_HPR = 11, B_ASN = 12, B_NAT = 13, B_ARB = 14, B_OTH = 15, NB = 16;
    static final String[] BLOC = {"White evangelicals (no degree)", "White evangelicals (degree)", "White Catholics (no degree)",
            "White Catholics (degree)", "Other whites (no degree)", "Other whites (degree)", "Jewish", "Latter-day Saints",
            "Black", "Hispanic (Mexican & other)", "Cuban & Venezuelan", "Puerto Rican & Dominican", "Asian American",
            "Native American", "Arab & Muslim American", "Multiracial & other"};
    static final int AGE_YOUNG = 0, AGE_MID = 1, AGE_OLD = 2, NAGE = 3;
    static final String[] AGE = {"18-29", "30-64", "65+"};
    static final int NSEX = 2;                       // 0 men, 1 women
    static final int NCELL = NB * NAGE * NSEX;

    static int cell(int b, int a, int s) { return (b * NAGE + a) * NSEX + s; }

    static int cellBloc(int c) { return c / (NAGE * NSEX); }

    static int cellAge(int c) { return (c / NSEX) % NAGE; }

    static int cellSex(int c) { return c % NSEX; }

    static boolean white(int b) { return b <= B_WOT_C; }

    static boolean hispanic(int b) { return b == B_HMX || b == B_HCU || b == B_HPR; }

    static boolean college(int b) { return b == B_WEV_C || b == B_WCA_C || b == B_WOT_C; }

    /** National two-party Democratic share of each bloc in 2024 (Pew validated voters, AP VoteCast, exit polls). */
    static final double[] BLOC_D24 = {0.15, 0.25, 0.34, 0.47, 0.41, 0.63, 0.69, 0.32, 0.86, 0.55, 0.32, 0.63, 0.59, 0.57, 0.52, 0.57};
    /** Weight of the state's own partisan offset for each bloc (regional variation is largest among whites). */
    static final double[] BLOC_STATE_W = {1, 1, 1, 1, 1, 1, 0.45, 0.8, 0.3, 0.6, 0.6, 0.6, 0.6, 0.6, 0.5, 0.8};
    /** How far each bloc moves with the national environment and campaigns (persuadability). */
    static final double[] BLOC_ELAST = {0.55, 0.75, 1.0, 1.0, 1.0, 1.0, 0.8, 0.85, 0.65, 1.25, 0.95, 1.15, 1.1, 0.9, 1.05, 1.0};
    /** Base turnout of eligible citizens. */
    static final double[] BLOC_TURN = {0.70, 0.84, 0.70, 0.83, 0.60, 0.80, 0.82, 0.74, 0.60, 0.48, 0.60, 0.50, 0.58, 0.50, 0.55, 0.55};
    /** Gender gap multiplier (2024: very large among Black and Hispanic voters). */
    static final double[] BLOC_GENDER = {0.6, 0.6, 0.8, 0.8, 0.85, 0.85, 0.6, 0.6, 2.0, 1.5, 1.0, 1.4, 0.9, 0.9, 0.7, 1.0};
    /** Age profile of each bloc's eligible voters {18-29, 30-64, 65+}. */
    static final double[][] BLOC_AGE = {
            {0.13, 0.54, 0.33}, {0.12, 0.56, 0.32}, {0.15, 0.54, 0.31}, {0.14, 0.56, 0.30}, {0.18, 0.54, 0.28}, {0.20, 0.57, 0.23},
            {0.17, 0.50, 0.33}, {0.25, 0.55, 0.20}, {0.24, 0.58, 0.18}, {0.31, 0.56, 0.13}, {0.20, 0.55, 0.25}, {0.29, 0.56, 0.15},
            {0.23, 0.59, 0.18}, {0.26, 0.58, 0.16}, {0.31, 0.57, 0.12}, {0.38, 0.52, 0.10}};
    /** Age x sex logit adjustments (2024: young men swung right, young women stayed left). */
    static final double[][] AGESEX_ADJ = {{-0.12, 0.50}, {-0.22, 0.16}, {-0.28, 0.10}};
    static final double[] AGE_ELAST = {1.30, 1.0, 0.85};
    static final double[] AGE_TURN = {0.70, 1.0, 1.15};

    // ---- overlays: fractions of each cell with a trait ----------------------------------------------------
    static final int O_RURAL = 0, O_SUBURB = 1, O_URBAN = 2, O_UNION = 3, O_VET = 4, O_GUN = 5, O_LGBT = 6, O_STUDENT = 7,
            O_PARENT = 8, NO = 9;
    static final String[] OVERLAY = {"Rural", "Suburban", "Urban", "Union household", "Veteran household", "Gun owner",
            "LGBTQ", "College student", "Parent of minor"};
    /** Partisan lean of each overlay relative to its cell (logit). */
    static final double[] OV_LEAN = {-0.55, 0.05, 0.45, 0.35, -0.35, -0.55, 1.2, 0.45, -0.10};

    // ---- the 30 voter groups shown to the player ----------------------------------------------------------
    static final int NG = 30;
    static final String[] GROUP = {"Gen Z (18-29)", "Seniors (65+)", "Young men", "Young women", "Women", "Men",
            "White evangelicals", "White Catholics", "White, no degree", "White, college degree", "Black voters", "Black men",
            "Hispanic voters", "Hispanic men", "Cuban & Venezuelan", "Puerto Rican & Dominican", "Asian Americans",
            "Jewish voters", "Arab & Muslim Americans", "Latter-day Saints", "Native Americans", "Suburban voters",
            "Rural voters", "Urban voters", "Union households", "Veterans & families", "Gun owners", "LGBTQ voters",
            "College students", "Parents"};
    /** For overlay-defined groups, which overlay; otherwise -1. */
    static final int[] GROUP_OV = {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            O_SUBURB, O_RURAL, O_URBAN, O_UNION, O_VET, O_GUN, O_LGBT, O_STUDENT, O_PARENT};

    /** Membership weight of cell c in group g (for non-overlay groups). */
    static double inGroup(int g, int c) {
        int b = cellBloc(c), a = cellAge(c), s = cellSex(c);
        switch (g) {
            case 0: return a == AGE_YOUNG ? 1 : 0;
            case 1: return a == AGE_OLD ? 1 : 0;
            case 2: return a == AGE_YOUNG && s == 0 ? 1 : 0;
            case 3: return a == AGE_YOUNG && s == 1 ? 1 : 0;
            case 4: return s == 1 ? 1 : 0;
            case 5: return s == 0 ? 1 : 0;
            case 6: return b == B_WEV_NC || b == B_WEV_C ? 1 : 0;
            case 7: return b == B_WCA_NC || b == B_WCA_C ? 1 : 0;
            case 8: return b == B_WEV_NC || b == B_WCA_NC || b == B_WOT_NC ? 1 : 0;
            case 9: return b == B_WEV_C || b == B_WCA_C || b == B_WOT_C ? 1 : 0;
            case 10: return b == B_BLK ? 1 : 0;
            case 11: return b == B_BLK && s == 0 ? 1 : 0;
            case 12: return hispanic(b) ? 1 : 0;
            case 13: return hispanic(b) && s == 0 ? 1 : 0;
            case 14: return b == B_HCU ? 1 : 0;
            case 15: return b == B_HPR ? 1 : 0;
            case 16: return b == B_ASN ? 1 : 0;
            case 17: return b == B_JEW ? 1 : 0;
            case 18: return b == B_ARB ? 1 : 0;
            case 19: return b == B_LDS ? 1 : 0;
            case 20: return b == B_NAT ? 1 : 0;
            default: return 0;
        }
    }

    // ---- issue opinion by bloc, age, sex and overlay ---------------------------------------------------------
    /** Bloc-specific shifts of the ideal point (added to the partisan blend). */
    static double blocIdealShift(int b, int i) {
        switch (b) {
            case B_WEV_NC: case B_WEV_C:
                return i == I_ISRAEL ? 0.40 : i == I_ABORT ? 0.40 : i == I_TRANS ? 0.30 : i == I_GUNS ? 0.20 : 0;
            case B_WCA_NC: case B_WCA_C:
                return i == I_ABORT ? 0.15 : 0;
            case B_JEW:
                return i == I_ISRAEL ? 0.70 : i == I_DEMOC ? -0.10 : i == I_GUNS ? -0.15 : 0;
            case B_LDS:
                return i == I_IMMIG ? -0.25 : i == I_DEMOC ? -0.30 : i == I_ABORT ? 0.20 : i == I_UKRAINE ? -0.20 : 0;
            case B_BLK:
                return i == I_ISRAEL ? -0.20 : i == I_TRANS ? 0.40 : i == I_ABORT ? 0.15 : i == I_IMMIG ? 0.20 : i == I_CRIME ? 0.05 : 0;
            case B_HMX:
                return i == I_IMMIG ? -0.15 : i == I_ABORT ? 0.15 : i == I_CRIME ? 0.10 : i == I_TRANS ? 0.15 : 0;
            case B_HCU:
                return i == I_CHINA ? 0.30 : i == I_ISRAEL ? 0.20 : i == I_HEALTH ? -0.10 : 0;
            case B_HPR:
                return i == I_HEALTH ? -0.10 : i == I_TRANS ? 0.10 : 0;
            case B_ASN:
                return i == I_CHINA ? -0.10 : i == I_EDU ? 0.15 : i == I_GUNS ? -0.20 : i == I_CRIME ? 0.15 : 0;
            case B_NAT:
                return i == I_CLIMATE ? -0.20 : i == I_GUNS ? 0.20 : 0;
            case B_ARB:
                return i == I_ISRAEL ? -1.10 : i == I_TRANS ? 0.50 : i == I_ABORT ? 0.30 : 0;
            default:
                return 0;
        }
    }

    static double blocSalMul(int b, int i) {
        switch (b) {
            case B_WEV_NC: case B_WEV_C: return i == I_ABORT ? 1.7 : i == I_TRANS ? 1.9 : i == I_ISRAEL ? 1.4 : 1;
            case B_WOT_C: case B_WCA_C: return i == I_DEMOC ? 1.5 : i == I_CLIMATE ? 1.3 : 1;
            case B_JEW: return i == I_ISRAEL ? 3.2 : i == I_DEMOC ? 1.3 : 1;
            case B_ARB: return i == I_ISRAEL ? 6.5 : 1;
            case B_BLK: return i == I_CRIME ? 1.2 : i == I_COL ? 1.1 : i == I_HEALTH ? 1.2 : 1;
            case B_HMX: case B_HPR: return i == I_IMMIG ? 1.3 : i == I_COL ? 1.2 : 1;
            case B_HCU: return i == I_CHINA ? 1.8 : i == I_IMMIG ? 1.2 : 1;
            case B_ASN: return i == I_CHINA ? 1.5 : i == I_EDU ? 1.3 : 1;
            case B_LDS: return i == I_DEMOC ? 1.3 : 1;
            default: return 1;
        }
    }

    static final double[][] AGE_IDEAL = {
            {-0.15, -0.10, -0.05, -0.05, -0.05, -0.45, -0.10, -0.10, -0.05, -0.05, 0.05, -0.10, -0.20, 0, -0.10, -0.30, 0.10, -0.25},
            {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0.10, 0.05, 0.05, 0, 0, 0.25, 0.05, 0.05, 0, 0, -0.15, 0.10, 0.05, 0, 0, 0.05, -0.15, 0.10}};
    static final double[][] AGE_SAL = {
            {0.8, 1.3, 1.0, 1.3, 0.8, 2.5, 2.0, 0.9, 1.1, 0.8, 0.35, 0.8, 1.5, 1.0, 1.1, 2.0, 0.7, 1.2},
            {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
            {1.2, 0.9, 1.0, 0.7, 1.0, 1.1, 0.5, 1.1, 0.9, 1.4, 2.4, 1.2, 0.8, 1.2, 0.9, 0.4, 1.3, 0.9}};
    static final double[][] SEX_IDEAL = {
            {0.05, 0.10, 0.03, 0.03, 0.03, 0.05, 0.03, 0.05, 0.10, 0.05, 0, 0.05, 0.05, 0.05, 0.12, 0.03, 0.05, 0.08},
            {-0.05, -0.10, -0.03, -0.03, -0.03, -0.05, -0.03, -0.05, -0.12, -0.05, 0, -0.05, -0.05, -0.05, -0.12, -0.03, -0.05, -0.08}};
    static final double[][] SEX_SAL = {
            {1.2, 1.1, 0.95, 1.05, 1.1, 1.0, 0.95, 1.05, 0.7, 0.9, 0.95, 1.1, 0.9, 1.0, 1.2, 0.9, 1.1, 1.0},
            {0.85, 0.9, 1.05, 0.95, 0.9, 1.0, 1.05, 0.95, 1.4, 1.15, 1.05, 0.95, 1.1, 1.0, 0.85, 1.1, 0.9, 1.0}};
    /** Overlay ideal shifts and salience multipliers (indexed [overlay][issue]). */
    static final double[][] OV_IDEAL = new double[NO][NI];
    static final double[][] OV_SAL = new double[NO][NI];

    static {
        for (int o = 0; o < NO; o++) Arrays.fill(OV_SAL[o], 1);
        OV_IDEAL[O_RURAL][I_AI] = -0.25; OV_SAL[O_RURAL][I_AI] = 1.4; OV_SAL[O_RURAL][I_GUNS] = 1.4; OV_IDEAL[O_RURAL][I_CLIMATE] = 0.2;
        OV_SAL[O_URBAN][I_CRIME] = 1.3; OV_SAL[O_URBAN][I_HOUSING] = 1.5; OV_IDEAL[O_URBAN][I_HOUSING] = -0.1;
        OV_SAL[O_SUBURB][I_COL] = 1.1; OV_SAL[O_SUBURB][I_ABORT] = 1.1; OV_SAL[O_SUBURB][I_EDU] = 1.1;
        OV_SAL[O_UNION][I_JOBS] = 1.6; OV_SAL[O_UNION][I_TRADE] = 1.8; OV_IDEAL[O_UNION][I_TRADE] = 0.3; OV_IDEAL[O_UNION][I_JOBS] = -0.3; OV_IDEAL[O_UNION][I_AI] = -0.2;
        OV_SAL[O_VET][I_CHINA] = 1.5; OV_SAL[O_VET][I_UKRAINE] = 1.3; OV_IDEAL[O_VET][I_CHINA] = 0.15;
        OV_SAL[O_GUN][I_GUNS] = 3.0; OV_IDEAL[O_GUN][I_GUNS] = 0.40;
        OV_SAL[O_LGBT][I_TRANS] = 4.0; OV_IDEAL[O_LGBT][I_TRANS] = -0.60; OV_SAL[O_LGBT][I_DEMOC] = 1.3;
        OV_SAL[O_STUDENT][I_EDU] = 3.0; OV_SAL[O_STUDENT][I_ISRAEL] = 1.6; OV_IDEAL[O_STUDENT][I_ISRAEL] = -0.3; OV_SAL[O_STUDENT][I_HOUSING] = 1.5;
        OV_SAL[O_PARENT][I_EDU] = 1.4; OV_SAL[O_PARENT][I_COL] = 1.2; OV_SAL[O_PARENT][I_TRANS] = 1.3;
    }

    // =====================================================================
    //  4.  IDEOLOGIES (FACTIONS)
    // =====================================================================
    static final int F_DSOC = 0, F_PROG = 1, F_LIB = 2, F_ABUN = 3, F_MOD = 4, F_OUTD = 5, F_MAGA = 6, F_NATCON = 7,
            F_TECHR = 8, F_HAWK = 9, F_GOV = 10, F_RESTR = 11, F_MAHA = 12, F_GREEN = 13, F_LIBT = 14, F_FWD = 15, F_AMP = 16, NF = 17;

    static final class Faction {
        final String name, desc, impact;
        final int party;
        final double econ, cult;
        final double[] pos;

        Faction(String name, int party, double econ, double cult, double[] pos, String desc, String impact) {
            this.name = name; this.party = party; this.econ = econ; this.cult = cult; this.pos = pos; this.desc = desc; this.impact = impact;
        }
    }

    static final Faction[] FACTIONS = {
            new Faction("Democratic Socialist", DEM, -0.90, -0.85,
                    new double[]{-0.30, -0.70, -0.95, -0.90, 0.10, -0.80, -0.60, -0.70, -1.0, -1.0, -0.90, -0.70, -0.95, -0.90, -0.80, -0.95, -0.30, -0.95},
                    "Medicare for All, taxing billionaires, a Green New Deal and confronting 'the oligarchy'. Heir to the Sanders movement.",
                    "Electrifies Gen Z, renters and progressive activists; huge small-dollar and volunteer engine. Costs ground with older moderates, suburban independents and some Hispanic men."),
            new Faction("Progressive Populist", DEM, -0.65, -0.60,
                    new double[]{0.00, -0.60, -0.80, -0.75, 0.15, -0.50, -0.30, -0.50, -0.95, -0.75, -0.80, -0.35, -0.80, -0.90, -0.75, -0.75, -0.60, -0.75},
                    "Economic fighter: anti-corporate, pro-union, tough on monopolies and corruption, eager to take the fight to Trump.",
                    "Strong with union households, young voters and the activist base; plays well with non-college voters on economics, less so on culture."),
            new Faction("Mainstream Liberal", DEM, -0.50, -0.60,
                    new double[]{0.10, -0.30, -0.65, -0.55, -0.20, -0.10, -0.10, -0.45, -0.95, -0.60, -0.65, -0.20, -0.75, -0.85, -0.75, -0.60, -0.80, -0.65},
                    "The Democratic center of gravity since 2020: the Biden-Harris agenda, abortion rights, democracy and the ACA.",
                    "Solid with Black voters, college-educated women and older Democrats. Struggles to win back non-college and young men."),
            new Faction("Abundance Liberal", DEM, -0.30, -0.55,
                    new double[]{0.25, 0.15, -0.45, -0.30, -0.45, -0.10, 0.60, -0.35, -0.90, -0.45, -0.50, 0.00, -0.65, -0.80, -0.60, -0.40, -0.80, -0.45},
                    "Build more housing, energy and infrastructure; cut red tape; competent, pro-growth liberalism.",
                    "Appeals to college graduates, young renters and suburban independents. Housing and permitting messages travel; union voters are wary of the tech tilt."),
            new Faction("Pragmatic Moderate", DEM, -0.25, -0.20,
                    new double[]{0.35, 0.00, -0.50, -0.40, -0.05, 0.25, 0.25, -0.10, -0.75, -0.45, -0.60, 0.20, -0.40, -0.70, -0.40, -0.30, -0.70, -0.20},
                    "Win-the-middle Democrat: kitchen-table economics, faith and patriotism, distance from the left on crime, borders and culture.",
                    "Best with independents, rural and exurban whites, Catholics and swing-state suburbs. Activists and Gen Z are cooler; base turnout can sag."),
            new Faction("Outsider Centrist", DEM, -0.15, -0.15,
                    new double[]{0.30, 0.20, -0.40, -0.20, 0.00, 0.00, 0.30, 0.00, -0.70, -0.50, -0.50, 0.25, -0.30, -0.60, -0.30, -0.30, -0.50, 0.00},
                    "Business or celebrity outsider: anti-politician, plain-spoken, ideologically flexible.",
                    "Draws low-propensity and disaffected voters, especially young men; lacks the institutional support that wins primaries."),
            new Faction("MAGA Populist", REP, 0.30, 0.90,
                    new double[]{0.80, 0.40, 0.10, 0.20, 0.90, 0.60, 0.40, 1.0, 0.50, 0.50, -0.30, 0.90, 0.90, 0.80, 0.90, 0.80, 0.70, 0.95},
                    "Trump's movement in pure form: tariffs, mass deportation, America First, war on the 'deep state'.",
                    "Commands the Republican primary electorate and non-college whites; mobilises low-propensity men. Repels college suburbs and many independents."),
            new Faction("National Conservative", REP, 0.10, 0.85,
                    new double[]{0.85, 0.20, 0.00, 0.00, 0.85, 0.50, 0.20, 0.95, 0.75, 0.30, -0.40, 0.85, 0.75, 0.70, 0.85, 0.70, 0.60, 0.95},
                    "The 'New Right': pro-worker, anti-Big Tech and anti-China, socially traditional - MAGA with a policy agenda.",
                    "Strong with the MAGA base and working-class whites, can reach union households; weak with college women and suburban moderates."),
            new Faction("Tech Right", REP, 0.75, 0.60,
                    new double[]{0.70, 0.95, 0.60, 0.60, 0.50, 0.60, 0.80, 0.70, 0.40, 0.70, 0.30, 0.80, 0.80, 0.60, 0.80, 0.90, 0.50, 0.80},
                    "Accelerationist: AI and energy dominance, deregulation, merit, school choice - Silicon Valley's turn right.",
                    "Excites young men, entrepreneurs and podcast audiences; data-center and AI-job anxieties make rural voters and seniors uneasy."),
            new Faction("Reagan Conservative", REP, 0.70, 0.70,
                    new double[]{0.95, 0.50, 0.60, 0.50, 0.20, 0.95, 0.60, 0.70, 0.80, 0.60, 0.20, 0.80, 0.70, 0.30, 0.85, 0.85, -0.60, 0.85},
                    "Peace through strength, free markets, the traditional conservative coalition; hawkish on China, Russia and Iran.",
                    "Strong with older Republicans, evangelicals, veterans, Cuban Americans and donors; MAGA activists distrust 'neocons'."),
            new Faction("Governing Conservative", REP, 0.60, 0.50,
                    new double[]{0.70, 0.50, 0.50, 0.40, 0.30, 0.70, 0.60, 0.70, 0.60, 0.50, 0.00, 0.80, 0.60, 0.20, 0.80, 0.85, -0.20, 0.80},
                    "Results-first Republican governor: tax cuts, schools, order and competence, with less drama.",
                    "Wins back college suburbs and independents; the base finds it bloodless."),
            new Faction("Restrainer / Paleo", REP, 0.15, 0.95,
                    new double[]{0.40, -0.30, 0.00, 0.00, 0.90, -0.20, 0.20, 1.0, 0.80, 0.30, -0.50, 0.85, 0.80, 0.50, 0.95, 0.80, 0.95, 0.95},
                    "No more foreign wars, skeptical of aid to Israel and Ukraine, nationalist on trade and immigration, anti-establishment to the core.",
                    "Fires up online populists and young men; alarms hawks, Jewish and evangelical voters and party donors."),
            new Faction("MAHA Populist", REP, -0.05, 0.35,
                    new double[]{0.30, -0.30, -0.20, -0.10, 0.40, 0.60, 0.00, 0.60, -0.10, 0.00, -0.50, 0.40, 0.20, 0.20, 0.30, 0.30, 0.80, 0.60},
                    "Make America Healthy Again: anti-pharma, anti-processed food, distrust of institutions; crosses party lines.",
                    "Draws 'crunchy' independents, health-conscious women and conspiracy-curious young voters; scientists and doctors recoil."),
            new Faction("Green", GRN, -0.95, -0.90,
                    new double[]{-0.60, -0.80, -1.0, -1.0, -0.10, -1.0, -0.80, -0.90, -1.0, -1.0, -1.0, -0.90, -1.0, -0.80, -0.70, -1.0, 0.40, -1.0},
                    "Eco-socialist and anti-war: end arms to Israel, Medicare for All, a rapid climate transition.",
                    "Pulls protest votes from young progressives and Arab and Muslim Americans; a spoiler in close states."),
            new Faction("Libertarian", LBT, 0.90, -0.40,
                    new double[]{0.00, 0.90, 1.0, 0.90, -0.90, -0.30, 1.0, -0.30, 0.00, 0.90, 0.70, -0.40, 0.50, -0.50, 1.0, 0.90, 0.70, -0.20},
                    "Minimal government at home and abroad: free trade, free markets, gun rights and civil liberties.",
                    "Draws disaffected Republicans and young men; usually 1-2% but decisive in close races."),
            new Faction("Forward / Reform", FWD, -0.20, -0.30,
                    new double[]{0.20, -0.20, -0.40, -0.70, -0.20, 0.00, 0.40, -0.10, -0.50, -0.40, -0.40, 0.00, -0.30, -0.70, -0.30, -0.20, -0.40, -0.20},
                    "Fix the system: ranked-choice voting, open primaries, a universal basic income for the AI age.",
                    "Appeals to tech-minded independents, young voters and the politically homeless."),
            new Faction("America Party Centrist", AMP, 0.35, 0.10,
                    new double[]{0.50, 0.70, 0.40, 0.30, -0.20, 0.30, 0.80, 0.40, -0.30, 0.20, 0.00, 0.50, 0.00, -0.40, 0.30, 0.40, 0.20, 0.30},
                    "Billionaire-backed revolt against both parties: cut the deficit, build, AI dominance, end the uniparty.",
                    "Pulls from both sides - independents, young men and moderate suburbanites; scrambles the map."),
    };
    static final double[] GENERIC_D = FACTIONS[F_LIB].pos;
    static final double[] GENERIC_R = FACTIONS[F_MAGA].pos;

    // =====================================================================
    //  5.  CANDIDATES
    //  Ratings are the game's estimates from public records (elections won, money raised, debate and interview
    //  reviews, approval ratings) as of autumn 2026. They are judgements for play, not facts.
    // =====================================================================
    /** Appearance for the stylised portrait. */
    static final class Look implements Serializable {
        private static final long serialVersionUID = 1L;
        boolean female;
        int skin, hair, style, facial, glasses, suit, tie;
        double gray, faceW = 1, jaw = 0.5, age = 0.5, brow = 0.5, smile = 0.5;
        boolean earrings, lipstick;

        /** "F|M skin hair style gray facial glasses faceW jaw suit tie age smile [E][L]" */
        static Look parse(String s) {
            String[] t = s.trim().split("\\s+");
            Look l = new Look();
            l.female = t[0].equals("F");
            l.skin = Integer.parseInt(t[1]); l.hair = Integer.parseInt(t[2]); l.style = Integer.parseInt(t[3]);
            l.gray = Double.parseDouble(t[4]); l.facial = Integer.parseInt(t[5]); l.glasses = Integer.parseInt(t[6]);
            l.faceW = Double.parseDouble(t[7]); l.jaw = Double.parseDouble(t[8]); l.suit = Integer.parseInt(t[9]); l.tie = Integer.parseInt(t[10]);
            l.age = Double.parseDouble(t[11]); l.smile = Double.parseDouble(t[12]);
            for (int i = 13; i < t.length; i++) { if (t[i].contains("E")) l.earrings = true; if (t[i].contains("L")) l.lipstick = true; }
            return l;
        }
    }

    static final class Cand implements Serializable {
        private static final long serialVersionUID = 1L;
        String id, first, last, role, homeAb, bio, pros, cons, spouse;
        int party, born, faction, home;
        double econ, cult;
        int[] a = new int[NA];
        double nameId, poll, cash, list, selfMax;
        double[] plat = new double[NI];
        String[] record = new String[0];
        int[] recIssue = new int[0];
        double[] recCred = new double[0];
        int[] apG = new int[0];
        double[] apV = new double[0];
        Look look;
        boolean custom, fictional;

        String name() { return first + " " + last; }

        int age2028() { return 2028 - born - 1 + (born > 0 ? 1 : 0); }
    }

    static final List<Cand> ROSTER = new ArrayList<Cand>();

    static int stateIdx(String ab) {
        for (int i = 0; i < STATES.length; i++) if (STATES[i].ab.equals(ab)) return i;
        return -1;
    }

    /** Compact roster entry. attrs: CHA POD SPE DEB INT DIS RET SMD BIG SELF VOL VIR AUT STA SCA EXE FPC XOV EST HOM BASE */
    static Cand cand(String id, String first, String last, int party, String role, String home, int born, int faction,
                     String attrs, double nameId, double poll, double cash, double list, double selfMax, String look) {
        Cand c = new Cand();
        c.id = id; c.first = first; c.last = last; c.party = party; c.role = role; c.homeAb = home; c.born = born; c.faction = faction;
        String[] t = attrs.trim().split("\\s+");
        for (int i = 0; i < NA; i++) c.a[i] = Integer.parseInt(t[i]);
        c.nameId = nameId; c.poll = poll; c.cash = cash * 1e6; c.list = list * 1e6; c.selfMax = selfMax * 1e6;
        c.econ = FACTIONS[faction].econ; c.cult = FACTIONS[faction].cult;
        c.plat = FACTIONS[faction].pos.clone();
        c.look = Look.parse(look);
        ROSTER.add(c);
        return c;
    }

    static void bio(Cand c, String bio, String pros, String cons) { c.bio = bio; c.pros = pros; c.cons = cons; }

    /** Record items: "issue:credibility:text" with issue -1 for general. */
    static void rec(Cand c, String... items) {
        c.record = new String[items.length]; c.recIssue = new int[items.length]; c.recCred = new double[items.length];
        for (int i = 0; i < items.length; i++) {
            String[] t = items[i].split(":", 3);
            c.recIssue[i] = Integer.parseInt(t[0]); c.recCred[i] = Double.parseDouble(t[1]); c.record[i] = t[2];
        }
    }

    /** Group appeal: "group:value" pairs (logit shifts among that group). */
    static void appeal(Cand c, String s) {
        String[] t = s.trim().split("\\s+");
        c.apG = new int[t.length]; c.apV = new double[t.length];
        for (int i = 0; i < t.length; i++) { String[] p = t[i].split(":"); c.apG[i] = Integer.parseInt(p[0]); c.apV[i] = Double.parseDouble(p[1]); }
    }

    static void pos(Cand c, double econ, double cult, String issues) {
        c.econ = econ; c.cult = cult;
        if (issues == null) return;
        for (String p : issues.trim().split("\\s+")) { String[] kv = p.split("="); c.plat[Integer.parseInt(kv[0])] = Double.parseDouble(kv[1]); }
    }

    // groups: 0 GenZ 1 Seniors 2 YoungMen 3 YoungWomen 4 Women 5 Men 6 WhiteEvang 7 WhiteCath 8 WhiteNoDeg 9 WhiteDeg 10 Black
    // 11 BlackMen 12 Hispanic 13 HispMen 14 Cuban 15 PR 16 Asian 17 Jewish 18 ArabMuslim 19 LDS 20 Native 21 Suburban 22 Rural
    // 23 Urban 24 Union 25 Veterans 26 GunOwners 27 LGBTQ 28 Students 29 Parents
    static {
        Cand c;
        // ---------------------------------------------------------------- DEMOCRATS
        c = cand("harris", "Kamala", "Harris", DEM, "Former Vice President", "CA", 1964, F_LIB,
                "70 55 72 80 75 64 58 92 90 10 85 74 50 70 22 62 68 40 88 60 65", 0.97, 22, 8, 9.0, 0, "F 4 1 7 0.10 0 0 0.98 0.45 0 0 0.6 0.75 E");
        bio(c, "Vice President 2021-25, senator and attorney general of California. Lost the 2024 election to Donald Trump 49.8-48.3 after replacing Joe Biden at the top of the ticket and raising more than $1 billion in fifteen weeks.",
                "Near-universal name recognition, a proven national fundraising machine, deep loyalty among Black voters and older Democrats.",
                "A third presidential run after a loss; tied to the Biden years; critics see her as scripted in long-form settings.");
        rec(c, "8:0.6:Led the administration's abortion-rights campaign after Dobbs", "2:0.3:As California AG, won $18bn for homeowners in the foreclosure settlement",
                "12:0.4:Cast tie-breaking votes for the Inflation Reduction Act", "-1:0:Raised $1 billion in 15 weeks in 2024");
        appeal(c, "10:0.14 4:0.04 3:0.05 11:-0.04 2:-0.05 8:-0.04");
        c.spouse = "Doug Emhoff";

        c = cand("newsom", "Gavin", "Newsom", DEM, "Governor of California (to Jan 2027)", "CA", 1967, F_ABUN,
                "82 80 80 84 80 62 64 88 92 15 75 92 45 85 35 82 55 35 80 55 85", 0.85, 18, 10, 6.0, 0, "M 1 3 3 0.45 0 0 1.0 0.7 1 1 0.55 0.7");
        pos(c, -0.42, -0.58, "6=0.45 1=0.25");
        bio(c, "Two-term governor of California and former mayor of San Francisco. Became the loudest Democratic voice against Trump in 2025, winning the Prop 50 redistricting fight and trolling the White House online.",
                "Polished, combative and everywhere: a top debater with his own podcast, a giant email list and California's donor base.",
                "California's cost of living, homelessness and taxes are a ready-made attack; 'slick coastal elite' image; the French Laundry dinner.");
        rec(c, "13:0.5:Won the Prop 50 redistricting fight against Trump (2025)", "3:0.3:Signed a $20 minimum wage for fast-food workers",
                "12:0.4:Phase-out of new gas-car sales and a cap-and-trade extension", "6:-0.3:Presided over a worsening housing and homelessness crisis");
        appeal(c, "9:0.05 27:0.06 8:-0.06 22:-0.06");
        c.spouse = "Jennifer Siebel Newsom";

        c = cand("buttigieg", "Pete", "Buttigieg", DEM, "Former Secretary of Transportation", "MI", 1982, F_ABUN,
                "75 92 85 90 92 88 72 80 80 5 72 80 60 80 15 55 55 55 65 45 70", 0.80, 11, 5, 3.0, 0, "M 1 1 2 0.05 0 0 0.95 0.5 0 0 0.35 0.7");
        bio(c, "Transportation Secretary 2021-25 and mayor of South Bend, Indiana; Navy Reserve veteran of Afghanistan. Won the 2020 Iowa caucuses. Moved to Michigan.",
                "Perhaps the party's best communicator in hostile settings - Fox News, manosphere podcasts, debates. Disciplined and policy-fluent.",
                "Struggled with Black voters in 2020; thin executive record; the East Palestine derailment and air-traffic control troubles.");
        rec(c, "6:0.3:Oversaw rollout of the bipartisan infrastructure law", "3:0.3:Infrastructure jobs in all 50 states", "16:0.2:Navy Reserve intelligence officer in Afghanistan");
        appeal(c, "9:0.10 27:0.10 10:-0.12 2:0.04 1:0.03");
        c.spouse = "Chasten Buttigieg";

        c = cand("aoc", "Alexandria", "Ocasio-Cortez", DEM, "U.S. Representative, New York", "NY", 1989, F_DSOC,
                "92 78 88 78 78 74 80 98 30 0 95 95 85 85 15 20 35 25 30 60 95", 0.80, 12, 15, 5.0, 0, "F 2 1 8 0 0 0 0.92 0.4 0 0 0.25 0.7 EL");
        bio(c, "Democratic socialist congresswoman from the Bronx and Queens since 2019; author of the Green New Deal; co-leader with Bernie Sanders of the 'Fighting Oligarchy' tour.",
                "The most magnetic figure on the left: unmatched small-dollar fundraising, huge crowds and a volunteer army.",
                "Republicans' favourite foil; no executive experience; worries about electability among older and moderate voters.");
        rec(c, "12:0.5:Author of the Green New Deal resolution", "2:0.4:Led the 'Fighting Oligarchy' tour with Bernie Sanders", "-1:0:Record small-dollar fundraising for a House member");
        appeal(c, "0:0.22 15:0.14 12:0.05 3:0.08 1:-0.08 8:-0.06 6:-0.05 17:-0.06");

        c = cand("shapiro", "Josh", "Shapiro", DEM, "Governor of Pennsylvania", "PA", 1973, F_MOD,
                "78 62 88 78 82 85 75 72 88 5 70 60 60 80 25 85 50 82 75 90 55", 0.50, 4.5, 5, 1.5, 0, "M 1 1 4 0.30 0 0 1.02 0.55 0 0 0.45 0.8");
        pos(c, -0.28, -0.32, "15=0.0 5=0.35");
        bio(c, "Governor of Pennsylvania since 2023 after winning by nearly 15 points; previously state attorney general. Running for re-election in November 2026.",
                "High approval in the ultimate swing state, a 'get stuff done' record and real crossover appeal with independents and Republicans.",
                "Pro-Israel record is a liability with young progressives and Arab Americans; support for school vouchers angers teachers' unions.");
        rec(c, "-1:0:Reopened I-95 in Philadelphia 12 days after its collapse", "6:0.3:Cut permitting and licensing backlogs", "11:0.3:As AG, exposed clergy abuse cover-ups",
                "13:0.2:Defended Pennsylvania's 2020 election in court");
        appeal(c, "17:0.15 21:0.05 24:0.05 7:0.04 18:-0.25 0:-0.05");
        c.spouse = "Lori Shapiro";

        c = cand("pritzker", "JB", "Pritzker", DEM, "Governor of Illinois", "IL", 1965, F_PROG,
                "62 55 78 65 75 70 55 55 80 100 60 70 55 65 30 80 40 40 75 70 85", 0.50, 3, 3, 0.8, 2500, "M 1 2 4 0.25 0 0 1.30 0.35 1 0 0.5 0.6");
        pos(c, -0.58, -0.62, null);
        bio(c, "Billionaire heir to the Hyatt fortune, governor of Illinois since 2019 and seeking a third term in 2026. Became a national figure fighting the 2025 National Guard and immigration deployments in Chicago.",
                "Can self-fund a whole campaign; combative anti-Trump record; a progressive governing record with balanced budgets.",
                "Easy to paint as an out-of-touch billionaire; average charisma on camera.");
        rec(c, "3:0.4:Signed a $15 minimum wage and paid leave for all workers", "13:0.4:Fought the federal troop deployment to Chicago", "8:0.4:Made Illinois an abortion-access haven");
        appeal(c, "17:0.08 24:0.05 22:-0.04");

        c = cand("whitmer", "Gretchen", "Whitmer", DEM, "Governor of Michigan (to Jan 2027)", "MI", 1971, F_MOD,
                "72 60 75 72 72 70 78 70 78 5 70 55 65 75 15 82 40 65 75 80 55", 0.60, 2, 4, 1.5, 0, "F 1 2 7 0.05 0 0 0.98 0.45 0 0 0.4 0.75 E");
        pos(c, -0.35, -0.45, "8=-0.9");
        bio(c, "Two-term governor of Michigan, term-limited in January 2027. 'Fix the damn roads.' Repealed right-to-work and won an abortion-rights amendment.",
                "Midwestern swing-state appeal, strong with suburban women and union households.",
                "Cosy appearances with Trump in 2025 annoyed the base; has hinted she may not run.");
        rec(c, "3:0.5:Repealed Michigan's right-to-work law", "8:0.5:Championed the Prop 3 abortion-rights amendment", "-1:0:Rebuilt thousands of miles of Michigan roads");
        appeal(c, "4:0.05 21:0.05 24:0.06");

        c = cand("beshear", "Andy", "Beshear", DEM, "Governor of Kentucky (to Dec 2027)", "KY", 1977, F_MOD,
                "70 65 72 70 75 88 82 55 65 5 55 45 80 80 10 82 35 90 65 90 45", 0.35, 2, 2, 0.8, 0, "M 1 2 2 0.05 0 0 1.0 0.5 0 0 0.35 0.75");
        pos(c, -0.25, -0.18, "17=-0.4");
        bio(c, "Twice elected governor of deep-red Kentucky (2019, 2023); chair of the Democratic Governors Association. A former attorney general and Sunday-school teacher who talks easily about faith.",
                "Proven to win Trump voters; scandal-free, disciplined, comfortable with rural and religious voters.",
                "Little known outside Kentucky; may be too mild for a base that wants a fighter; Kentucky's electoral votes are out of reach anyway.");
        rec(c, "3:0.4:Record economic development and job growth in Kentucky", "-1:0:Led recovery from the 2021-22 tornadoes and floods", "17:0.2:Vetoed an anti-trans bill");
        appeal(c, "22:0.12 6:0.08 8:0.08 7:0.04 0:-0.04");
        c.spouse = "Britainy Beshear";

        c = cand("moore", "Wes", "Moore", DEM, "Governor of Maryland", "MD", 1978, F_LIB,
                "88 70 90 72 78 72 75 70 82 10 75 65 60 80 30 65 55 60 75 80 60", 0.35, 1.5, 3, 1.0, 0, "M 5 0 1 0.05 0 0 1.0 0.6 0 0 0.35 0.8");
        pos(c, -0.42, -0.40, null);
        bio(c, "Governor of Maryland since 2023, Army combat veteran of Afghanistan, Rhodes Scholar, author and former CEO of an anti-poverty foundation. Says he is not running.",
                "Charismatic orator with a compelling biography; appeals across racial lines and to veterans.",
                "Short record; questions over a Bronze Star claim; Maryland budget troubles.");
        rec(c, "2:0.3:Signed the ENOUGH Act on child poverty", "16:0.3:Army paratrooper and captain in Afghanistan", "-1:0:Rebuilding the Key Bridge");
        appeal(c, "10:0.12 25:0.08 11:0.05");
        c.spouse = "Dawn Flythe Moore";

        c = cand("booker", "Cory", "Booker", DEM, "U.S. Senator, New Jersey", "NJ", 1969, F_LIB,
                "80 65 90 75 80 70 75 75 75 5 70 70 55 98 10 55 50 40 70 65 70", 0.65, 3.5, 6, 2.5, 0, "M 5 0 0 0 0 0 1.0 0.55 0 1 0.45 0.8");
        bio(c, "Senator from New Jersey since 2013 and former mayor of Newark. Set the Senate record with a 25-hour speech against the Trump administration in 2025. Ran for president in 2020.",
                "A tireless, inspiring speaker with deep ties to Black communities and the Senate.",
                "Sometimes seen as more performance than substance; 2020 campaign never caught fire.");
        rec(c, "13:0.3:Record 25-hour Senate speech against the administration", "11:0.3:Co-architect of the First Step Act on criminal justice", "-1:0:Mayor of Newark 2006-13");
        appeal(c, "10:0.10 11:0.04");

        c = cand("khanna", "Ro", "Khanna", DEM, "U.S. Representative, California", "CA", 1976, F_PROG,
                "60 80 65 72 85 70 65 70 60 15 60 65 65 75 10 15 45 40 25 40 70", 0.30, 1, 10, 1.2, 0, "M 3 0 2 0.10 0 0 0.95 0.45 0 0 0.4 0.7");
        pos(c, -0.62, -0.50, "4=0.35 1=0.0 0=0.45");
        bio(c, "Silicon Valley congressman since 2017 and co-chair of Bernie Sanders' 2020 campaign. Co-led the bipartisan push with Thomas Massie to release the Epstein files. Says he will decide after the midterms.",
                "A progressive who speaks tech; tireless media presence on every platform, including right-leaning podcasts.",
                "Low name recognition; no executive record; a House member has rarely won the nomination.");
        rec(c, "0:0.4:Co-author of the Endless Frontier bill that became the CHIPS Act", "13:0.4:Forced release of the Epstein files with Rep. Massie", "4:0.3:'Economic patriotism' plan to rebuild manufacturing");
        appeal(c, "16:0.10 0:0.08 28:0.06");

        c = cand("kelly", "Mark", "Kelly", DEM, "U.S. Senator, Arizona", "AZ", 1964, F_MOD,
                "65 60 62 62 82 85 70 85 75 10 65 60 80 75 10 40 85 75 70 80 60", 0.55, 3, 22, 3.0, 0, "M 1 6 0 0.8 4 0 1.0 0.6 1 1 0.75 0.6");
        pos(c, -0.25, -0.30, "14=-0.6 7=0.05");
        bio(c, "Senator from Arizona since 2020, retired Navy combat pilot and NASA astronaut, husband of former Rep. Gabby Giffords. Threatened with a court-martial review in 2025 over a video urging troops to refuse illegal orders.",
                "Unimpeachable biography, swing-state wins, the largest war chest of any potential candidate.",
                "Low-key on stage; may be too moderate and too cautious for primary voters.");
        rec(c, "16:0.7:Navy combat pilot in the Gulf War and four-time Space Shuttle astronaut", "14:0.4:Gun-safety advocate with Gabby Giffords", "-1:0:Won Arizona statewide twice");
        appeal(c, "25:0.12 21:0.04 22:0.03");
        c.spouse = "Gabby Giffords";

        c = cand("gallego", "Ruben", "Gallego", DEM, "U.S. Senator, Arizona", "AZ", 1979, F_PROG,
                "72 72 68 68 70 70 75 70 60 0 65 65 80 80 25 25 60 70 50 70 65", 0.30, 1, 3, 1.2, 0, "M 3 0 2 0 2 0 1.02 0.6 0 0 0.35 0.65");
        pos(c, -0.48, -0.25, "7=-0.05 11=0.2");
        bio(c, "Senator from Arizona since 2025, Marine Corps combat veteran of Iraq and son of immigrants; won Arizona by 2 points in 2024 while Trump carried it by 5.",
                "A working-class Latino Democrat who wins Hispanic men - exactly the voters the party lost.",
                "Short Senate tenure; personal life and past votes will be scrutinised; little national profile.");
        rec(c, "16:0.4:Marine combat veteran of Iraq", "-1:0:Outran Harris by 7 points in Arizona in 2024", "7:0.3:Voted for the Laken Riley Act on immigration enforcement");
        appeal(c, "13:0.18 12:0.10 25:0.06 2:0.04");

        c = cand("murphy", "Chris", "Murphy", DEM, "U.S. Senator, Connecticut", "CT", 1973, F_PROG,
                "68 75 75 75 82 75 60 82 60 0 65 78 70 75 10 15 75 35 55 65 85", 0.35, 1, 8, 2.5, 0, "M 1 2 2 0.10 0 0 0.95 0.5 0 0 0.4 0.6");
        bio(c, "Senator from Connecticut since 2013 after the Sandy Hook massacre shaped his career. One of the most aggressive anti-Trump voices online since 2025.",
                "Sharp, combative and policy-serious; a foreign-policy voice; strong small-dollar engine.",
                "A blue-state senator with little crossover appeal; never run nationally.");
        rec(c, "14:0.6:Negotiated the Bipartisan Safer Communities Act", "16:0.3:Foreign Relations Committee leader on Ukraine", "13:0.4:Anti-corruption crusade against the administration");
        appeal(c, "0:0.04 27:0.03");

        c = cand("ossoff", "Jon", "Ossoff", DEM, "U.S. Senator, Georgia", "GA", 1987, F_LIB,
                "78 65 82 75 78 80 70 85 75 5 75 70 65 85 10 20 45 55 60 75 70", 0.40, 1.5, 6, 3.0, 0, "M 1 1 5 0 0 0 0.95 0.45 0 0 0.25 0.6");
        bio(c, "Senator from Georgia since 2021, the youngest member of the Senate, on the ballot again in November 2026. A former investigative documentary producer.",
                "Young, eloquent, battle-tested in Georgia's brutal races; a prodigious fundraiser.",
                "Thin legislative record; must first win re-election in 2026.");
        rec(c, "13:0.5:Wrote the bill to ban stock trading by members of Congress", "11:0.3:Exposed abuse in federal prisons", "-1:0:Won Georgia twice");
        appeal(c, "0:0.08 10:0.06");

        c = cand("warnock", "Raphael", "Warnock", DEM, "U.S. Senator, Georgia", "GA", 1969, F_LIB,
                "82 60 95 75 80 85 80 80 65 0 75 55 80 75 25 15 35 55 60 75 65", 0.45, 1, 5, 2.5, 0, "M 5 0 1 0.35 4 0 1.0 0.5 0 1 0.5 0.75");
        bio(c, "Senator from Georgia since 2021 and senior pastor of Ebenezer Baptist Church, Martin Luther King Jr.'s congregation.",
                "A preacher's gift for oratory, moral authority and strong appeal to Black and religious voters.",
                "Little executive experience; a messy divorce dispute resurfaced in campaigns.");
        rec(c, "9:0.5:Capped insulin at $35 for seniors", "-1:0:Pastor of Ebenezer Baptist Church", "-1:0:Won Georgia twice, including a 2022 runoff");
        appeal(c, "10:0.15 6:0.04 1:0.03");

        c = cand("walz", "Tim", "Walz", DEM, "Governor of Minnesota", "MN", 1964, F_PROG,
                "72 55 68 55 65 50 85 70 55 0 70 60 85 70 45 70 50 55 60 50 70", 0.75, 2, 1, 1.5, 0, "M 1 7 2 0.9 0 1 1.10 0.5 3 0 0.8 0.75");
        bio(c, "Governor of Minnesota since 2019, former congressman, teacher, football coach and 24-year Army National Guard veteran. Kamala Harris's 2024 running mate.",
                "Folksy, authentic Midwestern populist with rural roots and a progressive record.",
                "Weak 2024 debate performance; a sprawling Minnesota fraud scandal on his watch; gaffe-prone.");
        rec(c, "2:0.4:Free school meals and paid family leave in Minnesota", "16:0.2:24 years in the Army National Guard", "13:-0.3:Minnesota social-service fraud scandal");
        appeal(c, "22:0.06 8:0.05 25:0.04 26:0.03");
        c.spouse = "Gwen Walz";

        c = cand("emanuel", "Rahm", "Emanuel", DEM, "Former Ambassador to Japan", "IL", 1959, F_MOD,
                "60 75 62 70 85 55 55 30 90 15 30 55 60 70 40 70 75 50 85 40 30", 0.45, 1, 1, 0.2, 0, "M 1 6 4 0.6 0 0 0.92 0.4 1 1 0.75 0.5");
        pos(c, -0.15, -0.12, "0=0.6 11=0.45 17=0.2");
        bio(c, "Obama's first White House chief of staff, two-term mayor of Chicago and Biden's ambassador to Japan. Arguing the party must reclaim the centre.",
                "Formidable strategist and fundraiser; deep foreign-policy and governing experience.",
                "Abrasive; the left distrusts him; the Laquan McDonald case still follows him.");
        rec(c, "0:0.5:Ambassador to Japan; built anti-China alliances", "15:0.3:Lengthened Chicago's school day", "9:0.2:Helped pass the Affordable Care Act as chief of staff");
        appeal(c, "17:0.08 0:-0.10 10:-0.06");

        c = cand("raimondo", "Gina", "Raimondo", DEM, "Former Secretary of Commerce", "RI", 1971, F_ABUN,
                "55 60 60 62 88 80 55 35 85 10 35 40 55 70 15 80 70 50 75 50 30", 0.25, 0.5, 1, 0.2, 0, "F 1 1 6 0.05 0 0 0.98 0.5 1 0 0.45 0.6 E");
        pos(c, -0.12, -0.40, "0=0.6 1=0.3");
        bio(c, "Commerce Secretary 2021-25 who ran the CHIPS Act and export controls on China; governor of Rhode Island and a former venture capitalist.",
                "Technocratic competence on chips, AI and China; well connected to business.",
                "Little grassroots following; progressive suspicion of her business ties.");
        rec(c, "0:0.6:Ran the CHIPS Act and chip export controls on China", "1:0.4:Built the U.S. AI Safety Institute", "10:-0.2:Cut Rhode Island pensions as treasurer");
        appeal(c, "7:0.05 9:0.04");

        c = cand("cuban", "Mark", "Cuban", DEM, "Businessman", "TX", 1958, F_OUTD,
                "78 90 60 72 78 45 70 60 60 100 55 90 85 70 40 70 25 70 15 65 55", 0.75, 2.5, 0, 0.5, 4000, "M 1 1 10 0.3 0 0 1.0 0.55 4 9 0.7 0.7");
        bio(c, "Billionaire entrepreneur, longtime owner of the Dallas Mavericks, 'Shark Tank' investor and founder of the Cost Plus Drugs pharmacy. Campaigned for Harris in 2024.",
                "Can self-fund; plain-spoken outsider with real crossover appeal and a health-care business that cut drug prices.",
                "No political experience; says he does not want the job; prone to off-the-cuff remarks.");
        rec(c, "9:0.5:Cost Plus Drugs: cut prices on hundreds of prescriptions", "-1:0:Built and sold Broadcast.com; owned the Dallas Mavericks");
        appeal(c, "2:0.12 5:0.05 0:0.04");

        c = cand("smith", "Stephen A.", "Smith", DEM, "ESPN commentator", "NY", 1967, F_OUTD,
                "85 88 75 70 55 35 65 55 40 40 50 95 80 75 45 10 10 60 5 50 50", 0.70, 2, 0, 0.5, 60, "M 5 0 0 0.2 5 0 1.0 0.6 2 2 0.6 0.7");
        pos(c, -0.20, -0.05, "7=0.15 11=0.4 17=0.3");
        bio(c, "Host of ESPN's 'First Take' and one of the most-watched sports commentators in America. Has openly mused about a 2028 run.",
                "Enormous reach with Black men, young men and sports fans; unscripted and electric on air.",
                "No political or governing experience; a long archive of hot takes.");
        rec(c, "-1:0:Built one of the biggest audiences in sports media");
        appeal(c, "11:0.25 2:0.12 5:0.06 10:0.06");

        // ---------------------------------------------------------------- REPUBLICANS
        c = cand("vance", "JD", "Vance", REP, "Vice President", "OH", 1984, F_NATCON,
                "62 85 70 88 88 70 55 85 92 15 85 80 45 80 25 40 60 30 90 70 90", 0.92, 42, 10, 6.0, 0, "M 1 1 2 0 1 0 1.05 0.55 0 1 0.3 0.6");
        bio(c, "Vice President since 2025, previously senator from Ohio, Marine veteran of Iraq and author of 'Hillbilly Elegy'. Finance chair of the RNC and widely seen as Trump's heir.",
                "Trump's blessing, MAGA donors, a strong debater and podcast guest; leads every primary poll.",
                "Net-negative favourability; tied to the administration's record; struggles with women and college-educated voters.");
        rec(c, "4:0.4:Leading voice for tariffs and reshoring", "0:0.3:Hawk on China trade", "16:0.2:Marine Corps veteran of Iraq", "2:0.1:Cast the tie-breaking vote on the One Big Beautiful Bill");
        appeal(c, "8:0.08 2:0.06 4:-0.06 9:-0.06");
        c.spouse = "Usha Vance";

        c = cand("rubio", "Marco", "Rubio", REP, "Secretary of State", "FL", 1971, F_HAWK,
                "72 65 82 78 85 75 60 70 88 5 60 55 55 75 15 55 95 60 85 80 70", 0.85, 18, 4, 1.5, 0, "M 2 0 2 0.10 0 0 0.95 0.5 0 2 0.4 0.65");
        pos(c, 0.55, 0.62, "4=0.6 16=0.2");
        bio(c, "Secretary of State and acting National Security Adviser since 2025; senator from Florida 2011-25; 2016 presidential candidate. The face of the 2026 Venezuela operation.",
                "Commanding foreign-policy credentials, polished speaker, appeal to Hispanic voters and donors; rising in primary polls.",
                "MAGA activists remember him as a 'neocon'; the 2016 debate collapse; owns the administration's foreign policy.");
        rec(c, "0:0.6:Architect of the administration's confrontation with China", "-1:0:Secretary of State and National Security Adviser", "3:0.2:Co-wrote the Paycheck Protection Program");
        appeal(c, "14:0.25 12:0.10 6:0.03 25:0.05 0:-0.03");
        c.spouse = "Jeanette Rubio";

        c = cand("trumpjr", "Donald", "Trump Jr.", REP, "Businessman", "FL", 1977, F_MAGA,
                "65 85 60 55 60 45 55 90 80 40 85 92 70 70 55 30 25 15 70 60 98", 0.88, 11, 5, 5.0, 50, "M 1 1 3 0.05 1 0 1.05 0.6 0 0 0.3 0.5");
        bio(c, "The president's eldest son, executive vice president of the Trump Organization, podcast host and MAGA's most effective surrogate and fundraiser.",
                "The Trump name and the MAGA list; owns the online right; unshakeable base loyalty.",
                "No government experience; polarising to independents and women; questions about the family's business deals.");
        rec(c, "-1:0:MAGA's top surrogate and fundraiser", "-1:0:Built a conservative media and venture business");
        appeal(c, "8:0.08 2:0.06 26:0.05 4:-0.10 9:-0.10 21:-0.06");

        c = cand("desantis", "Ron", "DeSantis", REP, "Governor of Florida (to Jan 2027)", "FL", 1978, F_MAGA,
                "45 50 62 68 82 75 40 60 75 0 60 50 50 75 25 88 45 45 55 75 70", 0.85, 6, 4, 2.0, 0, "M 1 1 2 0 0 0 1.05 0.7 0 2 0.35 0.4");
        pos(c, 0.62, 0.90, "4=0.4 16=0.3");
        bio(c, "Two-term governor of Florida, term-limited in January 2027; Navy JAG veteran; ran second to Trump in the 2024 primaries before dropping out.",
                "A big governing record and a 19-point re-election; disciplined and policy-driven; a 'non-Vance' lane for donors.",
                "Awkward retail politician; the 2024 campaign flopped; MAGA loyalists have not forgotten he challenged Trump.");
        rec(c, "15:0.4:Parental-rights and universal school-choice laws", "-1:0:Won re-election by 19 points in 2022", "11:0.3:Hurricane Ian recovery");
        appeal(c, "7:0.04 14:0.10 4:-0.04 0:-0.05");
        c.spouse = "Casey DeSantis";

        c = cand("youngkin", "Glenn", "Youngkin", REP, "Former Governor of Virginia", "VA", 1966, F_GOV,
                "70 60 68 65 75 82 75 50 90 70 55 45 60 75 15 75 40 70 75 55 50", 0.50, 1.5, 5, 0.8, 300, "M 1 6 2 0.75 0 0 0.95 0.55 5 0 0.6 0.8");
        bio(c, "Governor of Virginia 2022-26 and former co-CEO of the Carlyle Group. Won a blue-leaning state in 2021 on parental rights; Republicans lost Virginia badly in 2025.",
                "Suburban-friendly, scandal-free, wealthy and well connected to donors.",
                "No national base; MAGA sees him as a country-club Republican; Virginia's 2025 rout dented his brand.");
        rec(c, "15:0.4:Raised teacher pay and championed parental rights", "2:0.3:Cut $4bn in Virginia taxes", "-1:0:Former co-CEO of the Carlyle Group");
        appeal(c, "21:0.08 9:0.08 29:0.06");

        c = cand("haley", "Nikki", "Haley", REP, "Former UN Ambassador", "SC", 1972, F_HAWK,
                "68 55 72 80 78 78 72 55 85 5 50 45 50 80 15 70 85 75 60 60 25", 0.80, 3, 3, 1.5, 0, "F 3 1 7 0.05 0 0 0.95 0.5 0 0 0.45 0.75 E");
        pos(c, 0.55, 0.50, "7=0.6 16=-0.8");
        bio(c, "Two-term governor of South Carolina and UN ambassador in Trump's first term; Trump's last challenger in the 2024 primaries.",
                "Strong with independents, college-educated suburbanites and hawks; a sharp debater.",
                "The MAGA base sees her as disloyal; small primary lane; voters she wins often are not Republicans.");
        rec(c, "16:0.6:UN Ambassador who took a hard line on Russia, Iran and China", "-1:0:Removed the Confederate flag from South Carolina's statehouse", "-1:0:Ran second in the 2024 primaries");
        appeal(c, "9:0.12 21:0.10 4:0.05 16:0.05 8:-0.05");

        c = cand("cruz", "Ted", "Cruz", REP, "U.S. Senator, Texas", "TX", 1970, F_HAWK,
                "55 80 75 85 88 65 50 75 75 0 60 60 40 80 30 20 65 25 55 55 70", 0.85, 2, 5, 2.5, 0, "M 2 1 3 0.40 2 0 1.0 0.55 0 1 0.55 0.5");
        pos(c, 0.75, 0.80, "16=-0.2");
        bio(c, "Senator from Texas since 2013, former Texas solicitor general who argued before the Supreme Court, runner-up for the 2016 nomination; hosts the 'Verdict' podcast.",
                "Formidable debater and constitutional lawyer; long-form media fluency; a grassroots list from 2016.",
                "Widely disliked by colleagues; the Cancun episode; a hawk out of step with the restrainer right.");
        rec(c, "-1:0:Argued nine cases before the Supreme Court", "-1:0:Runner-up for the 2016 nomination", "5:0.4:Leading voice against antisemitism on the right");
        appeal(c, "6:0.05 12:0.03 4:-0.06");

        c = cand("hawley", "Josh", "Hawley", REP, "U.S. Senator, Missouri", "MO", 1979, F_NATCON,
                "58 65 68 72 85 75 55 70 55 0 55 55 60 80 20 30 45 35 40 65 75", 0.55, 1, 5, 1.5, 0, "M 1 2 2 0 0 0 0.95 0.5 0 0 0.3 0.5");
        pos(c, 0.05, 0.85, "1=-0.5 3=-0.3 13=0.2");
        bio(c, "Senator from Missouri since 2019 and former state attorney general. A populist who breaks with the White House on AI regulation, stock trading and labour.",
                "Policy entrepreneur of the working-class right; Yale-trained litigator.",
                "The January 6 fist-pump; little crossover appeal; clashes with Trump.");
        rec(c, "13:0.5:Wrote a ban on stock trading by members of Congress and the president", "1:0.5:Leading Republican voice for AI regulation and child online safety", "3:0.3:Backed labour-law reforms with the Teamsters");
        appeal(c, "24:0.06 8:0.05 6:0.05");

        c = cand("kemp", "Brian", "Kemp", REP, "Governor of Georgia (to Jan 2027)", "GA", 1963, F_GOV,
                "60 50 62 65 72 85 72 50 80 15 65 35 75 70 10 85 30 70 70 85 40", 0.55, 1, 5, 0.8, 0, "M 1 6 2 0.85 0 0 1.05 0.7 0 2 0.7 0.6");
        bio(c, "Two-term governor of Georgia, term-limited in January 2027. Beat Stacey Abrams twice and refused Trump's pressure to overturn Georgia's 2020 result.",
                "Swing-state winner with a strong record on jobs and a formidable Georgia machine; appeals to suburbs.",
                "MAGA grudges over 2020; low national profile; little charisma on the national stage.");
        rec(c, "3:0.5:Landed Hyundai's and Rivian's EV plants in Georgia", "-1:0:Beat Stacey Abrams twice", "13:0.3:Certified Georgia's 2020 result despite pressure");
        appeal(c, "21:0.06 22:0.05 10:0.03");

        c = cand("sanders", "Sarah", "Huckabee Sanders", REP, "Governor of Arkansas", "AR", 1982, F_MAGA,
                "62 55 65 65 70 78 65 65 70 0 60 55 55 75 30 65 30 35 80 75 80", 0.60, 1, 3, 1.2, 0, "F 1 1 7 0 0 0 1.0 0.5 0 0 0.3 0.6 E");
        pos(c, 0.60, 0.85, null);
        bio(c, "Governor of Arkansas since 2023 and Trump's White House press secretary 2017-19; daughter of former governor Mike Huckabee. On the ballot again in 2026.",
                "Trump-world credentials with a governing record; strong with evangelicals.",
                "Small-state profile; the lectern spending controversy; untested nationally.");
        rec(c, "15:0.5:Signed Arkansas LEARNS, universal school choice", "-1:0:Trump's White House press secretary");
        appeal(c, "6:0.08 29:0.03");

        c = cand("ramaswamy", "Vivek", "Ramaswamy", REP, "Republican nominee for Governor of Ohio", "OH", 1985, F_TECHR,
                "70 88 75 82 85 60 70 70 75 80 60 82 45 90 25 35 30 40 50 60 80", 0.65, 3, 5, 1.5, 500, "M 3 0 2 0 0 0 0.92 0.45 0 0 0.25 0.7");
        bio(c, "Biotech entrepreneur, 2024 presidential candidate and briefly co-chair of DOGE; the Trump-endorsed nominee for Ohio governor in November 2026.",
                "Relentless energy, podcast-native, can self-fund; popular with young men and the tech right.",
                "Seen as glib by older voters; thin governing record unless he wins Ohio.");
        rec(c, "-1:0:Founded Roivant Sciences", "2:0.2:Co-chaired DOGE at its launch", "15:0.3:Champion of merit-based education");
        appeal(c, "16:0.08 2:0.08 1:-0.06");

        c = cand("carlson", "Tucker", "Carlson", REP, "Media personality", "FL", 1969, F_RESTR,
                "78 95 70 70 75 55 50 80 55 25 70 90 70 70 55 10 30 25 25 30 90", 0.80, 3, 2, 3.0, 40, "M 1 4 11 0.45 0 0 1.0 0.5 3 0 0.6 0.4");
        bio(c, "Former Fox News prime-time host turned independent media giant; interviewed Putin in 2024 and drew fire from fellow conservatives over a 2025 interview with Nick Fuentes.",
                "One of the most influential voices on the right, with a vast audience and podcast fluency.",
                "Feuds with hawks and Jewish conservatives; no governing experience; a trail of controversies.");
        rec(c, "16:0.3:Leading voice against aid to Ukraine", "-1:0:Built the largest independent conservative media platform");
        appeal(c, "2:0.08 8:0.04 17:-0.25 9:-0.08 25:-0.03");

        c = cand("greene", "Marjorie", "Taylor Greene", REP, "Former U.S. Representative, Georgia", "GA", 1974, F_RESTR,
                "60 75 60 55 50 30 60 80 20 10 65 90 85 75 60 10 15 15 5 45 70", 0.75, 2, 3, 2.5, 0, "F 1 5 7 0 0 0 1.0 0.5 0 0 0.4 0.6 E");
        pos(c, 0.15, 0.85, "9=0.0 13=-0.2");
        bio(c, "Congresswoman from Georgia 2021-26 who broke with Trump in 2025 over the Epstein files, health-care costs and foreign wars, then resigned from Congress.",
                "Fierce populist following, viral reach and a reputation for saying what she thinks.",
                "Toxic with most general-election voters; now feuding with Trump and the MAGA establishment.");
        rec(c, "13:0.3:Pushed release of the Epstein files", "9:0.2:Broke with her party over health-care costs");
        appeal(c, "22:0.04 4:-0.08 9:-0.15");

        c = cand("rfk", "Robert F.", "Kennedy Jr.", REP, "Secretary of Health and Human Services", "CA", 1954, F_MAHA,
                "65 85 62 55 70 40 60 70 60 20 65 75 70 70 70 50 20 50 40 30 55", 0.90, 4, 2, 2.5, 10, "M 1 2 11 0.5 0 0 0.92 0.55 0 1 0.85 0.55");
        bio(c, "Health and Human Services Secretary since 2025, environmental lawyer and nephew of President John F. Kennedy; ran as an independent in 2024 before endorsing Trump.",
                "MAHA movement, a famous name, real crossover appeal to independents and health-conscious parents.",
                "Vaccine controversies; would be 74; doctors, scientists and much of the GOP establishment distrust him.");
        rec(c, "9:0.3:HHS Secretary: the 'Make America Healthy Again' agenda", "12:0.3:Riverkeeper environmental lawyer", "-1:0:2024 independent candidate");
        appeal(c, "29:0.06 4:0.05 0:0.03 1:-0.04 9:-0.03");

        // ---------------------------------------------------------------- THIRD PARTIES
        c = cand("stein", "Jill", "Stein", GRN, "Physician and activist", "MA", 1950, F_GREEN,
                "45 50 50 50 65 55 45 40 5 5 35 45 70 50 30 5 10 10 0 10 60", 0.45, 0, 0.5, 0.5, 0, "F 1 6 6 0.9 0 1 0.95 0.4 1 0 0.85 0.5");
        bio(c, "Physician and the Green Party's presidential nominee in 2012, 2016 and 2024.", "Owns the anti-war protest vote over Gaza.", "Tiny organisation; accused of spoiling for Republicans.");
        appeal(c, "18:0.6 0:0.15 28:0.1");
        c = cand("yang", "Andrew", "Yang", FWD, "Forward Party co-founder", "NY", 1975, F_FWD,
                "70 85 62 68 82 65 70 55 30 10 50 70 75 75 15 30 20 60 5 5 50", 0.70, 0, 1, 1.0, 0, "M 2 0 2 0.15 0 0 0.95 0.5 0 0 0.4 0.75");
        bio(c, "Entrepreneur, 2020 Democratic candidate who popularised universal basic income, and co-founder of the Forward Party.", "AI-age message for the politically homeless.", "Thin support beyond the online; ballot access is patchy.");
        appeal(c, "16:0.10 0:0.08 2:0.06");
        c = cand("libert", "Dana", "Whitfield", LBT, "Libertarian nominee (fictional)", "NH", 1979, F_LIBT,
                "55 65 55 55 70 60 55 35 20 5 40 45 65 70 15 20 15 35 5 10 50", 0.10, 0, 0.3, 0.2, 0, "F 1 3 6 0 0 0 1.0 0.5 3 0 0.4 0.6");
        c.fictional = true;
        bio(c, "A fictional Libertarian Party nominee: a former state legislator and small-business owner.", "The Libertarian ballot line in all 50 states.", "Little money, little attention.");
        appeal(c, "2:0.06 26:0.04");
        c = cand("amp", "Reid", "Callahan", AMP, "America Party nominee (fictional)", "TX", 1980, F_AMP,
                "72 85 68 65 82 65 60 60 90 100 55 85 60 80 35 75 30 75 5 30 60", 0.35, 0, 80, 1.0, 1500, "M 1 3 5 0.1 2 0 1.0 0.55 4 9 0.4 0.6");
        c.fictional = true;
        bio(c, "A fictional tech founder running on the America Party line with billionaire backing.", "Unlimited money and a post-partisan pitch to the exhausted middle.", "No party infrastructure; ballot access fights; attacked by both parties.");
        appeal(c, "2:0.08 21:0.05");

        for (Cand x : ROSTER) x.home = stateIdx(x.homeAb);
    }

    static Cand candById(String id) { for (Cand c : ROSTER) if (c.id.equals(id)) return c; return null; }

    static List<Cand> roster(int party) {
        List<Cand> l = new ArrayList<Cand>();
        for (Cand c : ROSTER) if (c.party == party) l.add(c);
        return l;
    }

    // ---- sandbox archetypes: base ratings and a point pool ----------------------------------------------------
    static final String[] ARCHETYPE = {"Governor", "Senator", "House member", "Big-city mayor", "Cabinet secretary", "General / admiral",
            "Billionaire CEO", "Celebrity", "Activist / organiser", "Podcaster / media star", "Faith leader"};
    static final String[] ARCH_BASE = {
            "60 55 62 60 68 70 65 55 65 5 55 50 60 70 25 80 40 55 65 75 55",
            "60 60 65 65 72 68 58 65 62 5 55 55 55 70 25 30 60 50 65 65 60",
            "58 62 60 60 66 62 62 62 45 5 55 60 60 72 25 15 35 40 45 50 65",
            "62 58 62 58 66 62 70 50 60 5 60 55 60 70 30 70 30 45 55 50 55",
            "52 55 55 58 78 75 50 35 70 5 40 40 55 70 15 70 65 50 70 40 40",
            "60 55 60 55 72 85 55 40 55 5 55 45 75 75 15 70 95 70 50 40 50",
            "62 70 55 58 75 55 55 45 75 95 45 65 60 70 40 75 30 55 30 40 45",
            "82 75 70 55 50 45 65 65 50 50 60 92 70 70 45 10 10 60 10 40 55",
            "72 70 72 62 65 60 70 75 30 0 85 70 80 75 25 10 15 30 25 40 85",
            "72 92 62 65 66 50 55 70 40 20 60 90 75 75 40 5 15 55 10 30 70",
            "75 60 85 62 70 80 70 55 45 5 70 50 75 70 20 20 20 55 40 45 60"};
    static final int SANDBOX_POINTS = 120;

    // =====================================================================
    //  6.  CAMPAIGN ACTIONS, MESSAGES, PROMISES, SCENARIOS
    // =====================================================================
    // ---- itinerary activities --------------------------------------------------------------------------------
    static final int ACT_NONE = 0, ACT_RALLY = 1, ACT_TOWNHALL = 2, ACT_RETAIL = 3, ACT_FUNDRAISER = 4, ACT_CALLTIME = 5,
            ACT_PODCAST = 6, ACT_TV = 7, ACT_HOSTILE = 8, ACT_SPEECH = 9, ACT_UNION = 10, ACT_CHURCH = 11, ACT_CAMPUS = 12,
            ACT_COMMUNITY = 13, ACT_PREP = 14, ACT_REST = 15, ACT_ENDORSE = 16, ACT_DINNER = 17, NACT = 18;
    static final String[] ACT_NAME = {"Unscheduled", "Rally", "Town hall", "Retail stops", "High-dollar fundraiser", "Bundler meetings & call time",
            "Podcast / long-form", "TV interview", "Crossover media", "Policy speech", "Union hall", "Faith community", "Campus event",
            "Community event", "Debate prep", "Rest", "Endorsement meeting", "Party dinner"};
    static final String[] ACT_SHORT = {"-", "RALLY", "TOWN HALL", "RETAIL", "FUNDRAISER", "CALL TIME", "PODCAST", "TV", "CROSSOVER",
            "SPEECH", "UNION", "CHURCH", "CAMPUS", "COMMUNITY", "PREP", "REST", "ENDORSE", "DINNER"};
    /** Does the activity happen in a state (true) or is it national/remote (false)? */
    static final boolean[] ACT_LOCAL = {false, true, true, true, true, false, false, false, false, true, true, true, true, true, false, false, false, true};
    /** Event cost to the campaign (dollars), fatigue cost (stamina points). */
    static final double[] ACT_COST = {0, 350000, 90000, 25000, 60000, 0, 10000, 5000, 5000, 120000, 40000, 20000, 60000, 40000, 50000, 0, 0, 30000};
    static final double[] ACT_FATIGUE = {0, 9, 6, 7, 4, 3, 4, 3, 5, 5, 6, 5, 6, 6, 5, -14, 2, 5};
    static final String[] ACT_DESC = {
            "Nothing planned in this slot.",
            "A big rally: fires up the base, recruits volunteers and dominates local news. Scales with charisma, speeches and volunteer energy.",
            "A town hall with undecided voters: persuasion in the state, but unscripted questions risk a gaffe.",
            "Diners, fairs and handshakes. Small but sticky persuasion; huge in Iowa, New Hampshire and South Carolina.",
            "A high-dollar dinner. Raises the most in donor hubs (New York, California, Texas, Florida, Illinois, DC). Optics risk if overused.",
            "Meetings with bundlers and hours on the phone with donors, from anywhere. Raises money quietly; scales with your big-donor network.",
            "A long-form podcast or interview. Reaches young men and the politically disengaged nationwide; rewards authenticity and intellect.",
            "Sunday shows, cable and late-night. National exposure; tests discipline.",
            "An interview on the other side's turf (Fox News for Democrats, CNN or MSNBC for Republicans). Crossover appeal, real risk.",
            "A major policy address on your focus issue. Builds credibility on that issue and moves its salience.",
            "Speak at a union hall: union households and working-class voters in the state.",
            "Visit a church or faith community (Black churches, evangelical, Catholic, synagogues, mosques).",
            "A campus event: students and young voters; volunteers.",
            "An event in Black, Hispanic, Asian American or other communities in the state.",
            "Debate preparation and mock debates. Essential before debates; diminishing returns.",
            "Rest. Restores stamina; tired candidates make mistakes.",
            "Court governors, members of Congress, unions and groups for their endorsement.",
            "A state party dinner or cattle call with activists. Big in early primary states."};

    // ---- weekly message frames ------------------------------------------------------------------------------
    static final String[] FRAME = {"Cost-of-living fighter", "Competence & a steady hand", "Change: shake up Washington", "Defend our freedoms",
            "Strength & security", "Unity: turn the page", "Clean up corruption", "Build the future", "Working-class populism",
            "Law & order", "Faith, family & values", "America First", "Protect Social Security & Medicare", "My story"};
    /** Issues each frame raises in voters' minds. */
    static final int[][] FRAME_ISSUES = {{I_COL, I_JOBS}, {I_CHINA, I_UKRAINE}, {I_DEMOC, I_COL}, {I_ABORT, I_DEMOC}, {I_CHINA, I_IMMIG},
            {I_DEMOC}, {I_DEMOC}, {I_AI, I_HOUSING}, {I_TRADE, I_JOBS}, {I_CRIME, I_IMMIG}, {I_ABORT, I_TRANS}, {I_TRADE, I_IMMIG},
            {I_SOCSEC, I_HEALTH}, {}};
    static final String[] FRAME_DESC = {
            "Groceries, rent, gas and electricity bills. The issue voters rank first.",
            "Experience and calm in a dangerous world. Strong in a crisis or war.",
            "Outsider energy against a broken system. Strong when voters want change.",
            "Abortion rights and democracy. Mobilises women and college-educated voters.",
            "China, the border and a strong military.",
            "Lower the temperature; appeal across party lines. Rewards crossover appeal.",
            "Drain the swamp - stock-trading bans, ethics, accountability. Strong amid scandal.",
            "Housing, energy, AI and infrastructure: an optimistic build agenda.",
            "Trade, jobs and the forgotten worker. Union and non-college voters.",
            "Crime, policing and public safety.",
            "Faith, family and traditional values. Evangelicals and religious voters.",
            "Tariffs, borders and no more foreign wars.",
            "Hands off seniors' benefits. Older voters, everywhere.",
            "The candidate's own biography and character."};

    // ---- attack lines ---------------------------------------------------------------------------------------
    static final String[] ATTACK = {"Stay positive", "Too extreme", "Out-of-touch elite", "Corrupt / bought", "Not ready to lead",
            "Puppet of the party's fringe", "Flip-flopper", "Weak on crime and the border", "Will cut Social Security", "Too old",
            "Chaos and division"};
    static final String[] ATTACK_DESC = {
            "No contrast this week. Avoids backlash, gives up the chance to define your opponent.",
            "Works when the opponent's platform is far from the median voter.",
            "Works against wealthy, coastal or establishment opponents with low authenticity.",
            "Works when the opponent has scandal baggage, big-donor ties or ties to an unpopular president.",
            "Works against candidates with thin executive and foreign-policy records.",
            "Ties the opponent to the other party's least popular faction.",
            "Works when the opponent has made promises that contradict their record.",
            "Works against Democrats with reform positions on policing and immigration.",
            "Works against Republicans who want to trim entitlements.",
            "Works against candidates over 70.",
            "Works against undisciplined, polarising opponents."};

    // ---- policy promises ------------------------------------------------------------------------------------
    static final class Promise {
        final String name, desc;
        final int issue;
        final double target, valence, donor;
        final int[] g;
        final double[] v;

        Promise(String name, int issue, double target, double valence, double donor, String groups, String desc) {
            this.name = name; this.issue = issue; this.target = target; this.valence = valence; this.donor = donor; this.desc = desc;
            if (groups.isEmpty()) { g = new int[0]; v = new double[0]; } else {
                String[] t = groups.split("\\s+");
                g = new int[t.length]; v = new double[t.length];
                for (int i = 0; i < t.length; i++) { String[] p = t[i].split(":"); g[i] = Integer.parseInt(p[0]); v[i] = Double.parseDouble(p[1]); }
            }
        }
    }

    // groups: 0 GenZ 1 Seniors 2 YoungMen 3 YoungWomen 4 Women 5 Men 6 Evang 7 Cath 8 WhiteNoDeg 9 WhiteDeg 10 Black 11 BlackMen
    // 12 Hisp 13 HispMen 14 Cuban 15 PR 16 Asian 17 Jewish 18 Arab 19 LDS 20 Native 21 Suburb 22 Rural 23 Urban 24 Union
    // 25 Vets 26 Gun 27 LGBTQ 28 Students 29 Parents
    static final Promise[] PROMISES = {
            new Promise("Ban stock trading by members of Congress", I_DEMOC, -0.6, 0.05, 0, "", "Wildly popular across parties; members of Congress hate it."),
            new Promise("Moratorium on new hyperscale data centers", I_AI, -0.8, 0.01, -0.15, "22:0.06 0:0.03 24:-0.03", "Until the grid is upgraded and ratepayers protected. Popular where bills are rising; tech donors furious."),
            new Promise("Make data centers pay for their own power", I_AI, -0.3, 0.03, -0.05, "22:0.03 1:0.02", "Shield household electricity bills from AI demand."),
            new Promise("National AI safety licensing", I_AI, -0.6, 0.01, -0.10, "1:0.02", "Licensing and testing for frontier AI models."),
            new Promise("Win the AI race: build, build, build", I_AI, 0.9, 0, 0.10, "22:-0.04 1:-0.02 2:0.02", "Fast-track data centers, power plants and chip fabs."),
            new Promise("AI displacement insurance for workers", I_JOBS, -0.7, 0.02, -0.03, "24:0.03 9:0.02", "Wage insurance and retraining for jobs lost to automation."),
            new Promise("Universal basic income pilot", I_JOBS, -0.95, -0.01, -0.05, "0:0.03 1:-0.02", "A national UBI experiment funded by an AI dividend."),
            new Promise("Build 4 million homes; override local zoning", I_HOUSING, 0.7, 0.02, 0.02, "0:0.05 21:-0.02", "Federal pressure to end exclusionary zoning."),
            new Promise("National rent-increase cap", I_HOUSING, -0.9, 0.01, -0.12, "0:0.05 23:0.03 21:-0.02", "Cap annual rent increases nationwide."),
            new Promise("$25,000 down-payment help for first-time buyers", I_HOUSING, -0.4, 0.02, 0, "0:0.04 29:0.02", "For first-generation homebuyers."),
            new Promise("Cancel up to $50,000 in student debt", I_EDU, -0.95, -0.01, -0.03, "0:0.06 28:0.05 8:-0.04 1:-0.02", "Broad student-debt cancellation."),
            new Promise("Universal school choice", I_EDU, 0.95, 0, 0.02, "29:0.03 6:0.03 10:0.01", "Let education dollars follow the child."),
            new Promise("Medicare for All", I_HEALTH, -1.0, -0.01, -0.15, "0:0.04 1:-0.02", "Single-payer national health insurance."),
            new Promise("Public option and $2,000 drug-cost cap for all", I_HEALTH, -0.55, 0.03, -0.03, "1:0.03", "Expand the ACA and cap out-of-pocket drug costs."),
            new Promise("Expand Social Security; lift the payroll-tax cap", I_SOCSEC, -0.9, 0.02, -0.08, "1:0.06", "Higher benefits funded by taxing high earners."),
            new Promise("Raise the retirement age to 69", I_SOCSEC, 0.8, -0.04, 0.05, "1:-0.10", "Shore up the trust fund by working longer."),
            new Promise("60% tariff on all Chinese goods", I_TRADE, 0.95, -0.01, -0.05, "24:0.03 8:0.02", "Decouple from China on trade."),
            new Promise("Repeal the tariffs to cut prices", I_TRADE, -0.8, 0.02, 0.05, "24:-0.02", "End the Trump-era tariffs."),
            new Promise("Defend Taiwan explicitly", I_CHINA, 1.0, 0, 0.02, "25:0.02", "End strategic ambiguity."),
            new Promise("Condition military aid to Israel", I_ISRAEL, -0.6, 0, -0.06, "18:0.20 0:0.04 28:0.04 17:-0.06 6:-0.04", "Aid tied to humanitarian law."),
            new Promise("Arms embargo on Israel", I_ISRAEL, -1.0, -0.02, -0.12, "18:0.35 0:0.06 28:0.06 17:-0.15 6:-0.06", "Halt offensive weapons transfers."),
            new Promise("Unwavering support for Israel", I_ISRAEL, 1.0, 0, 0.06, "17:0.05 6:0.04 18:-0.25 0:-0.04", "No conditions, no daylight."),
            new Promise("Mass deportation program", I_IMMIG, 1.0, -0.01, -0.02, "12:-0.06 8:0.02", "Remove millions of undocumented immigrants."),
            new Promise("Secure the border and an earned path to citizenship", I_IMMIG, 0.0, 0.03, 0.02, "12:0.03", "Bipartisan border bill plus legalisation."),
            new Promise("Codify Roe v. Wade", I_ABORT, -1.0, 0.01, 0, "4:0.03 3:0.04 6:-0.05", "Restore national abortion rights by law."),
            new Promise("National 15-week abortion limit", I_ABORT, 0.6, -0.02, 0, "6:0.04 4:-0.04 3:-0.04", "A federal floor with exceptions."),
            new Promise("Leave abortion to the states; veto any national ban", I_ABORT, 0.2, 0.02, 0, "4:0.02 6:-0.03", "The Trump formula."),
            new Promise("Assault-weapons ban", I_GUNS, -0.9, 0.0, 0, "26:-0.06 21:0.02", "Ban military-style rifles and high-capacity magazines."),
            new Promise("National concealed-carry reciprocity", I_GUNS, 0.9, 0, 0, "26:0.04 21:-0.02", "Carry permits valid in every state."),
            new Promise("Fund 100,000 new police officers", I_CRIME, 0.6, 0.02, 0, "21:0.02 10:0.01", "A national public-safety surge."),
            new Promise("Green industrial jobs plan; rejoin Paris", I_CLIMATE, -0.8, 0, -0.02, "0:0.03 24:0.01", "Clean-energy manufacturing and climate diplomacy."),
            new Promise("Drill baby drill; end EV mandates", I_CLIMATE, 0.9, 0, 0.04, "22:0.02 0:-0.03", "Maximise oil and gas production."),
            new Promise("$3,600 child tax credit", I_COL, -0.6, 0.03, -0.02, "29:0.06", "Restore and expand the pandemic-era credit."),
            new Promise("No tax on tips or overtime", I_COL, 0.2, 0.03, 0, "12:0.03 8:0.02", "Popular across the board."),
            new Promise("Billionaire minimum tax", I_COL, -0.5, 0.03, -0.12, "", "A minimum tax on unrealised gains of the ultra-rich."),
            new Promise("Cap credit-card interest at 10%", I_COL, -0.5, 0.04, -0.06, "", "A populist idea both parties have floated."),
            new Promise("Cut income taxes across the board", I_COL, 0.8, 0.01, 0.06, "", "Make the 2025 cuts bigger."),
            new Promise("Balanced-budget amendment", I_COL, 0.6, 0.01, 0.03, "1:0.01", "Force Washington to live within its means."),
            new Promise("Supreme Court term limits", I_DEMOC, -0.7, 0.01, 0, "9:0.02", "18-year terms for justices."),
            new Promise("Pledge: no pardons for allies, an independent DOJ", I_DEMOC, -0.6, 0.02, 0, "9:0.02", "Restore post-Watergate norms."),
            new Promise("Back Ukraine until Russia withdraws", I_UKRAINE, -1.0, 0, 0.02, "25:0.02", "Weapons and money for as long as it takes."),
            new Promise("Negotiate an end to the Ukraine war now", I_UKRAINE, 0.8, 0, 0, "2:0.02", "A ceasefire deal with Moscow."),
            new Promise("Protect gender-affirming care", I_TRANS, -1.0, -0.01, 0, "27:0.08 6:-0.04", "Federal protection for trans youth care."),
            new Promise("Keep males out of girls' sports", I_TRANS, 0.7, 0.02, 0, "27:-0.06 29:0.02", "A position majorities support."),
            new Promise("Pass the PRO Act: union card check", I_JOBS, -0.7, 0.01, -0.06, "24:0.07", "Make it easier to organise."),
            new Promise("National right-to-work law", I_JOBS, 0.9, -0.01, 0.06, "24:-0.08", "End compulsory union dues everywhere."),
    };

    // ---- scenarios -------------------------------------------------------------------------------------------
    static final int SC_BASE = 0, SC_SCANDAL = 1, SC_WAR = 2, SC_AIREC = 3, SC_BOOM = 4, SC_THIRD = 5, NSCEN = 6;
    static final String[] SCEN_NAME = {"Baseline 2028", "The Scandal", "War in the Pacific", "The AI Recession", "Boom Times", "Third Force"};
    static final String[] SCEN_TAG = {"Projected from the 2026 midterms", "Corruption craters the president's approval",
            "America is at war with China", "AI layoffs tip the economy into recession", "Strong growth and falling prices",
            "A billionaire-backed independent surges"};
    static final String[] SCEN_DESC = {
            "Democrats ride a midterm wave in November 2026 on cost-of-living anger and a president with approval in the high 30s. History says midterm waves fade - the party that loses a midterm often wins the next presidential race, but not always (1994, 2010 and 2018 were followed by mixed results). No incumbent is on the ballot. Begins near D+2 nationally with wide uncertainty.",
            "A sprawling corruption scandal - pardons for sale, crypto self-dealing, a cabinet resignation - drives the president's approval into the low 30s. Republicans must choose between loyalty and distance. Democracy and corruption become top-tier issues. Starts near D+6.",
            "A US-China war over Taiwan breaks out in spring 2027. A brief rally-round-the-flag effect gives way to casualties, shortages, a stock crash and draft fears. Foreign-policy credibility matters as never before; restrainers and hawks fight for the GOP soul.",
            "Agentic AI wipes out entry-level white-collar jobs. Unemployment climbs past 7%, data-center power demand sends electricity bills soaring and voters turn on the tech industry. AI and jobs become top issues; the party in power is blamed.",
            "Productivity gains from AI, falling inflation and a stock-market boom lift the economy; the president's approval climbs toward 50%. A tough year for the out-party: Democrats must offer more than opposition. Starts near R+2.",
            "Fed up with both parties, a billionaire funds the America Party and a fictional tech founder, Reid Callahan, polls in the high teens. Every state becomes a three-way race, and 270 is harder to reach."};
    static final String[] SCEN_SHORT = {
            "Democrats ride a midterm wave against a president in the high 30s. History says waves fade - but not always. The fundamentals lean slightly Democratic.",
            "Pardons for sale and crypto self-dealing push the president's approval into the low 30s. Corruption becomes a top issue; Republicans must choose loyalty or distance.",
            "War over Taiwan in spring 2027: a brief rally effect, then casualties, shortages and draft fears. Commander-in-chief credibility matters as never before.",
            "AI agents wipe out entry-level white-collar jobs. Unemployment passes 7%, power bills soar near data centers, and the party in power is blamed.",
            "AI productivity, falling inflation and a stock boom lift the president toward 50%. A tough year for the out-party: opposition alone won't do.",
            "A billionaire-funded America Party ticket polls in the high teens. Every state becomes a three-way race and 270 gets harder to reach."};
    /** Initial national environment (D-minus-R points), presidential approval, unemployment, inflation, GDP growth. */
    static final double[][] SCEN_START = {
            {2.0, 38, 4.6, 2.9, 1.6}, {6.0, 32, 4.8, 3.1, 1.2}, {1.0, 44, 5.4, 4.5, 0.4}, {5.0, 37, 7.2, 2.6, -1.4}, {-2.0, 49, 3.8, 2.0, 3.4}, {2.0, 38, 4.6, 2.9, 1.6}};

    // ---- midterm defaults (editable): House seats won by Democrats, Senate seats held by Democrats after Nov 2026, popular vote ----
    static final int MID_HOUSE_D = 236, MID_SENATE_D = 50;
    static final double MID_GENERIC = 7.0;

    // ---- polling --------------------------------------------------------------------------------------------
    static final String[] POLL_METHOD = {"Online panel", "Text-to-web", "Live phone + text"};
    static final double[] POLL_COST_PER = {28, 45, 95};          // dollars per completed interview
    static final double[] POLL_QUALITY = {0.65, 0.85, 1.0};       // inverse of extra error
    static final int[] POLL_SIZES = {400, 600, 800, 1200, 1500};

    // ---- digital platforms -------------------------------------------------------------------------------------
    static final String[] DIGITAL = {"Meta (Facebook/Instagram)", "YouTube & Google", "Streaming TV (CTV)", "Creators & influencers"};
    static final double[] DIGITAL_CPM = {14, 18, 38, 22};
    /** Audience skew by age (young, mid, old) - multiplies reach. */
    static final double[][] DIGITAL_AGE = {{0.8, 1.1, 1.2}, {1.3, 1.0, 0.7}, {0.6, 1.1, 1.1}, {2.4, 0.8, 0.2}};

    // ---- national TV --------------------------------------------------------------------------------------------
    static final String[] NATIONAL_TV = {"National network (sports & prime)", "Cable news", "Spanish-language networks"};
    static final double[] NATIONAL_CPP = {52000, 13000, 6500};

    // ---- tones ---------------------------------------------------------------------------------------------------
    static final String[] TONE = {"Positive", "Contrast", "Negative"};

    // ---- surrogates ----------------------------------------------------------------------------------------------
    static final String[][] SURROGATES = {
            {"Barack Obama", "0", "90", "Former president. Electrifies Democrats and Black voters; raises money anywhere."},
            {"Michelle Obama", "0", "88", "Former first lady. The party's most popular figure; rarely campaigns."},
            {"Bernie Sanders", "0", "80", "Senator from Vermont. Young voters and the left; union halls."},
            {"Bill Clinton", "0", "55", "Former president. Older Southern and suburban voters; baggage."},
            {"Donald Trump", "1", "92", "The sitting president. Electrifies the MAGA base; repels suburban moderates."},
            {"Melania Trump", "1", "60", "First lady. Rare appearances, soft-focus appeal."},
            {"Elon Musk", "1", "45", "Tech billionaire. Young men and the online right; toxic with many others."},
            {"Ron Johnson", "1", "40", "Wisconsin senator. Rural Wisconsin."}};

    // ---- debate hosts ---------------------------------------------------------------------------------------------
    static final String[] HOSTS = {"ABC News", "CBS News", "NBC News", "CNN", "Fox News", "NewsNation", "Univision", "A podcast host"};
    /** Lean of each host's moderators: negative favours Democrats, positive Republicans (as each side perceives it). */
    static final double[] HOST_LEAN = {-0.25, -0.15, -0.15, -0.20, 0.35, 0.05, -0.05, 0.10};

    // ---- endorsers ------------------------------------------------------------------------------------------------
    static final class Endorser {
        final String name;
        final int party, group;
        final double weight, money, vols;

        Endorser(String name, int party, int group, double weight, double money, double vols) {
            this.name = name; this.party = party; this.group = group; this.weight = weight; this.money = money; this.vols = vols;
        }
    }

    static final Endorser[] ENDORSERS = {
            new Endorser("AFL-CIO", DEM, 24, 0.08, 4e6, 0.08), new Endorser("SEIU", DEM, 24, 0.05, 3e6, 0.06),
            new Endorser("United Auto Workers", DEM, 24, 0.06, 1e6, 0.05), new Endorser("Teamsters", -1, 24, 0.06, 1e6, 0.04),
            new Endorser("NEA & AFT (teachers)", DEM, 29, 0.05, 3e6, 0.06), new Endorser("Planned Parenthood Action", DEM, 4, 0.05, 2e6, 0.04),
            new Endorser("Congressional Black Caucus", DEM, 10, 0.08, 0, 0.03), new Endorser("Congressional Hispanic Caucus", DEM, 12, 0.05, 0, 0.02),
            new Endorser("Sunrise Movement", DEM, 0, 0.05, 0, 0.08), new Endorser("J Street", DEM, 17, 0.03, 0.5e6, 0.01),
            new Endorser("League of Conservation Voters", DEM, 9, 0.03, 2e6, 0.02), new Endorser("Human Rights Campaign", DEM, 27, 0.04, 1e6, 0.02),
            new Endorser("NRA", REP, 26, 0.06, 2e6, 0.03), new Endorser("Turning Point Action", REP, 2, 0.06, 3e6, 0.08),
            new Endorser("Club for Growth", REP, 9, 0.03, 5e6, 0.01), new Endorser("Susan B. Anthony Pro-Life America", REP, 6, 0.05, 2e6, 0.05),
            new Endorser("Faith & Freedom Coalition", REP, 6, 0.06, 1e6, 0.06), new Endorser("Americans for Prosperity", REP, 21, 0.04, 6e6, 0.05),
            new Endorser("Fraternal Order of Police", -1, 21, 0.04, 0.2e6, 0.01), new Endorser("Republican Jewish Coalition", REP, 17, 0.03, 1e6, 0.01)};

    // =====================================================================
    //  7.  GAME STATE
    // =====================================================================
    static final int NS = 51;                         // states incl. DC
    static final int NSLOT = 21;                      // 7 days x 3 slots
    static final int PH_INVISIBLE = 0, PH_PRIMARY = 1, PH_CONVENTION = 2, PH_GENERAL = 3, PH_OVER = 4;
    static final String[] PHASE = {"Invisible primary", "Primary season", "Summer & conventions", "General election", "Election over"};
    static final int MODE_FULL = 0, MODE_GENERAL = 1;
    static final int ELECTION_WEEK = 104;             // week of Tuesday 7 November 2028 (week 0 starts Monday 9 November 2026)
    static final int GENERAL_START_WEEK = 94;         // Monday 28 August 2028
    static final int RNC_WEEK = 88, DNC_WEEK = 93;    // RNC Houston mid-July, DNC late August
    static final int EARLY_VOTE_WEEK = 97;            // mail ballots go out / early voting begins in many states

    static final class Settings implements Serializable {
        private static final long serialVersionUID = 1L;
        int mode = MODE_FULL, scenario = SC_BASE, difficulty = 1, party = DEM;
        String candId = "shapiro";
        Cand custom;
        long seed = System.nanoTime();
        int midHouseD = MID_HOUSE_D, midSenateD = MID_SENATE_D;
        double midGeneric = MID_GENERIC;
        boolean[] third = {true, true, true, false};   // Green, Libertarian, Forward, America Party
        double eventRate = 0.75;                        // between rare (0.5) and realistic (1)
        double[] platform;                              // edited starting platform
        String platformFor;
        int[] demEarly = {stateIdx("SC"), stateIdx("NV"), stateIdx("NH"), stateIdx("NM"), stateIdx("MI"), stateIdx("VA")};
        int[] repEarly = {stateIdx("IA"), stateIdx("NH"), stateIdx("NV"), stateIdx("SC")};
        boolean godMode;
        String opponentId;
        boolean[] staff = new boolean[5];               // staff automation: schedule, ads, field, polls, message
        List<String> sitOut = new ArrayList<String>();  // candidates who will not run (field selection)
        String lockD, lockR;                            // nominees the player has locked in (null = let the primaries decide)
        int indStart = 0;                               // independent run: 0 = Labor Day, 1 = from the start of the primaries
        int staffPolling = 1;                           // 0 light, 1 standard, 2 heavy
        boolean tutorial, tourDone;                     // the optional tutorial: on/off, and whether the headquarters tour has been taken
        List<String> tipsSeen = new ArrayList<String>();   // first-time tips already shown

        String lockOf(int party) { return party == DEM ? lockD : party == REP ? lockR : null; }
    }

    static final class InternalPoll implements Serializable {
        private static final long serialVersionUID = 1L;
        int week, state, n, method;                     // state -1 = national
        double margin, moe;                             // D minus R (points) or own share minus leader in primaries
        double[] share;                                 // per campaign index
        double ind = Double.NaN;                        // the main independent's share (general election, when one is running)
        double[] mip, trust;                            // issue module: most important issue shares; trust margin (you minus rival)
        double[] groupMargin = new double[NG];          // crosstabs (NaN if not measured)
        double cost;
        boolean pub;                                    // public (media) poll
        String pollster;
        int camp = -1;                                  // campaign that paid for it (internal polls)
    }

    static final class News implements Serializable {
        private static final long serialVersionUID = 1L;
        int week, kind, state = -1;
        String text;
        News(int week, int kind, String text) { this.week = week; this.kind = kind; this.text = text; }
    }

    static final int RS_SMALL = 0, RS_EVENTS = 1, RS_CALL = 2, RS_SELF = 3, RS_GROUPS = 4, RS_PARTY = 5, RS_N = 6;
    static final String[] RS_NAME = {"Small-dollar online", "Fundraisers & bundlers", "Call time", "Self-funding", "Allied groups & unions", "Party & joint committees"};

    static final int NK_INFO = 0, NK_GOOD = 1, NK_BAD = 2, NK_BIG = 3, NK_RESULT = 4, NK_EVENT = 5;

    /** A podcast or interview appearance and how it did. */
    static final class Appearance implements Serializable {
        private static final long serialVersionUID = 1L;
        int week, day, show, state = -1;
        double views, raised, signups, nameGain, quality;
        boolean viral, declined;
    }

    /** A rally and its crowd. */
    static final class Rally implements Serializable {
        private static final long serialVersionUID = 1L;
        int week, day, state;
        double crowd;
        String venue;
    }

    /** One campaign in the race (primary candidate, nominee or third-party candidate). */
    static final class Camp implements Serializable {
        private static final long serialVersionUID = 1L;
        Cand c;
        int idx, party;
        boolean human, active = true, announced, nominee;
        int announceWeek = -1, dropWeek = -1;
        double[] plat;
        int[] attr;                                     // effective attributes (fatigue and growth applied)
        // money
        double cash, raised, spent, smallList, donorsTapped, selfFunded, debt;
        double[] raisedHist = new double[110], spentHist = new double[110];
        double[] raisedBy = new double[RS_N];          // by source (small-dollar, events, call time, self, groups, party)
        int hq = 1;                                     // 0 lean, 1 standard, 2 juggernaut
        // image and message
        double nameId, enthusiasm = 0.5, momentum, favor;           // favor = net favourability shift (logit units)
        double[] imageG = new double[NG];
        double[] cred = new double[NI];
        double[] focus = new double[NI];
        double[] frameStr = new double[FRAME.length];
        int frame = 0, attack = 0, focusIssue = I_COL, lastFrame = -1, attackTarget = -1;
        double[] attackStick = new double[ATTACK.length];   // attacks against this campaign that have stuck
        double negativity;                              // backlash pool from going negative
        List<Integer> promises = new ArrayList<Integer>();
        double flipflop;
        List<Integer> endorsements = new ArrayList<Integer>();
        double scandalRevealed, oppoOnOpponent;
        int oppoFiles;                                  // research files ready to leak
        List<String> vetted = new ArrayList<String>();  // running mates vetted
        boolean[] known;                                // attributes the player can see exactly (own, revealed or researched)
        // stamina and debate
        double stamina = 100, prep, prepPeak;
        // itinerary
        int[] act = new int[NSLOT], loc = new int[NSLOT], param = new int[NSLOT];
        int location;
        int[] surrogateState = new int[8];
        int vpSurrogateState = -1;
        // per-state campaign assets
        double[] tvStock = new double[NS], digStock = new double[NS], toneStock = new double[NS];
        double[] visit = new double[NS], localImage = new double[NS];
        double[] offices = new double[NS], organizers = new double[NS], volunteers = new double[NS];
        double[] partyOrg = new double[NS];             // state-party organisers funded by transfers (coordinated campaign)
        double[] shiftsW = new double[NS], doorsW = new double[NS], callsW = new double[NS], textsW = new double[NS], relW = new double[NS];
        double[] shiftsT = new double[NS], doorsT = new double[NS], callsT = new double[NS], textsT = new double[NS], relT = new double[NS];
        double[] persuade = new double[NS], gotv = new double[NS];
        double[] spendState = new double[NS], spendWeek = new double[NS], transfers = new double[NS];
        double[] spend4 = new double[NS];               // recent spending (decaying sum, about four weeks)
        double[] superPac = new double[NS];
        // standing orders
        double[] tvGrp;                                 // per market weekly GRPs
        int[] tvTone;
        double[] nationalGrp = new double[NATIONAL_TV.length];
        double[] digital = new double[NS];              // dollars per week per state
        int digPlatform = 1, digTarget = -1;
        double[] transferW = new double[NS];
        int[] officeOrder = new int[NS], organizerOrder = new int[NS];
        boolean[] redBox = new boolean[NS];
        // primaries
        double delegates;
        double[] primShare = new double[NS];
        boolean[] stateWon = new boolean[NS];
        double primaryVotes;
        // history for charts
        double[] pollHist = new double[110];
        double[] volHist = new double[110], doorsHist = new double[110];
        // AI and primary calibration
        double aggression = 0.5, pInt;
        double pInt0, drift, inherit;                   // opening-poll edge (fades), random walk, support inherited from dropouts
        double selfFundW;                               // weekly self-funding transfer
        // name recognition by state (national = population-weighted average in nameId)
        double[] nameIdS = new double[NS];
        // issue focus: main issue is focusIssue; two secondary issues
        int[] focus2 = {-1, -1};
        int[] focusWeeks = new int[NI];
        // digital: national platform mix, message per platform, age-targeted persuasion stock per state
        double[] digMix;
        int[] digMsg;
        double[][] digA = new double[NS][NAGE];
        // the trail: rallies and media appearances
        List<Rally> rallies = new ArrayList<Rally>();
        List<Appearance> media = new ArrayList<Appearance>();
        double rallyCrowds;
        int rallyCount;
        // deals made for endorsements
        List<String> deals = new ArrayList<String>();
        String vpPromised;
        int speakers;
        // closing argument (final stretch)
        int closing = -1, closingSwitches;
        double closingStr;
        // ballot access (independents)
        boolean[] onBallot;
        double[] sigs = new double[NS], petitionW = new double[NS];
        Camp(Cand c, int idx) {
            this.c = c; this.idx = idx; party = c.party;
            plat = c.plat.clone();
            attr = c.a.clone();
            nameId = c.nameId;
            Arrays.fill(nameIdS, c.nameId);
            cash = c.cash;
            smallList = c.list;
            known = new boolean[NA];
            location = c.home >= 0 ? c.home : 0;
            Arrays.fill(surrogateState, -1);
        }

        String name() { return c.name(); }

        String last() { return c.last; }

        void attributeKnown(int i) { known[i] = true; }

        double a(int k) { return attr[k]; }
    }

    static final class Contest implements Serializable {
        private static final long serialVersionUID = 1L;
        int party, state, week;
        boolean caucus, wta, done;
        double delegates;
        double[] result;                                // vote share per campaign idx
        double[] won;                                   // delegates won per campaign idx
        String label;
    }

    static final class Debate implements Serializable {
        private static final long serialVersionUID = 1L;
        int week, host, party = -1;                     // party -1 = general election debate
        boolean vp, done, mutedMics, audience;
        int length = 90;
        double[] score;                                 // per campaign idx
        List<Integer> participants = new ArrayList<Integer>();
        String summary;
        int thresholdPoll, thresholdDonors;
    }

    static final class SenateRace implements Serializable {
        private static final long serialVersionUID = 1L;
        int state, party;
        String incumbent, note;
        double base, margin;                            // D-minus-R
        boolean open;
    }

    static final class Game implements Serializable {
        private static final long serialVersionUID = 1L;
        static final int VERSION = 2;
        int version = VERSION;                          // saves from an older version read as 0
        Settings set;
        int week, phase, player = -1;
        Random rng;
        List<Camp> camps = new ArrayList<Camp>();
        int dNom = -1, rNom = -1;
        Cand vpD, vpR, vpI;                             // running mates

        Cand vpOf(int party) { return party == DEM ? vpD : party == REP ? vpR : party == IND ? vpI : null; }

        void setVp(int party, Cand c) { if (party == DEM) vpD = c; else if (party == REP) vpR = c; else if (party == IND) vpI = c; }
        // world
        double approval, unemp, inflation, gdp, env, envDrift, consumer;
        double[] salience = new double[NI];
        double[] newsSal = new double[NI];
        double houseD, senateD;
        boolean demHouse;
        // results and polling
        List<InternalPoll> polls = new ArrayList<InternalPoll>();
        double[] pubAvg = new double[NS];               // public polling averages (D minus R)
        double[] pubN = new double[NS];
        double natPubAvg;
        double[] natHist = new double[110], natIHist = new double[110];
        List<News> news = new ArrayList<News>();
        List<Contest> contests = new ArrayList<Contest>();
        List<Debate> debates = new ArrayList<Debate>();
        List<SenateRace> senate = new ArrayList<SenateRace>();
        // early voting
        double[][] banked = new double[NS][4];          // votes already cast: D, R, all third parties, the main independent
        // final result
        double[][] result;                              // per state: D, R, third votes
        int evD, evR, evO;
        double popD, popR, popO;
        int[] cdWinner = new int[5];
        // pending decisions
        List<Object[]> pending = new ArrayList<Object[]>();
        String lastWeekSummary = "";
        int debateTalks;                                // 0 none, 1 negotiating, 2 agreed
        int[] debateDeal = {3, 0, 1, 0};                // count, first week offset, VP debate (0/1), host preference
        double trumpEndorse = -1;                       // camp idx Trump has endorsed in the GOP primary
        boolean contested;
        int clinchWeekD = -1, clinchWeekR = -1;
        double mood = 1.0;                              // overall turnout mood
        double natErr;                                  // systematic polling error this cycle (D-minus-R points)
        double[] stateErr = new double[NS];
        double[] houseEffect = new double[12];
        double[] senateNoise = new double[NS];
        double warWeeks;                                // weeks since war began (war scenario)
        int warStart = -1;

        double[] primaryPoll;                           // national primary polling average by campaign
        int day = -1;                                   // -1: weekly turns; 0-6: the day of the week in the final stretch
        double[][] mipHist = new double[110][], trustHist = new double[110][];
        int[] trustRival = new int[110];
        double houseMargin = Double.NaN;
        int houseSeatsD = -1;
        int contingent = -1;                            // party chosen by the House when nobody reaches 270
        int evI;                                        // electoral votes won by the main independent
        double popI;
        double[] pubI = new double[NS];                 // public polling average of the main independent's share (points)
        double natPubI;
        transient boolean dirty = true;
        int lastEvent = -10, pendingState = -1;
        boolean spectator;                              // the player's campaign is over; the race plays on

        double turnoutMood() { return mood; }

        Camp camp(int i) { return i >= 0 && i < camps.size() ? camps.get(i) : null; }

        Camp me() { return camp(player); }

        Camp opp() {
            Camp m = me();
            if (m == null) return null;
            if (m.party == IND) { Camp d = genCand(DEM), r = genCand(REP); return d == null ? r : r == null ? d : natPubAvg >= 0 ? d : r; }
            if (phase >= PH_CONVENTION) return camp(m.party == DEM ? rNom : dNom);
            return null;
        }

        boolean humanInd() { for (Camp k : camps) if (k.human && k.party == IND) return true; Camp m = me(); return m != null && m.party == IND; }

        Camp nominee(int party) { return camp(party == DEM ? dNom : rNom); }

        /** The main independent: a human independent, otherwise the America Party candidate if running. */
        Camp indep() {
            Camp best = null;
            for (Camp k : camps) if (k.active && k.party == IND) return k;
            for (Camp k : camps) if (k.active && k.party == AMP) best = k;
            return best;
        }

        /** The party's candidate for general-election polling: the nominee, or the primary leader before one is chosen. */
        Camp genCand(int party) {
            Camp n = nominee(party);
            if (n != null || primaryPoll == null) return n;
            Camp best = null;
            for (Camp k : camps) if (k.party == party && k.active && (best == null || primaryPoll[k.idx] + k.delegates / 4000.0 > primaryPoll[best.idx] + best.delegates / 4000.0)) best = k;
            return best;
        }

        boolean daily() { return day >= 0; }

        List<Camp> party(int p) {
            List<Camp> l = new ArrayList<Camp>();
            for (Camp k : camps) if (k.party == p && k.active && (k.announced || k.human || phase >= PH_PRIMARY)) l.add(k);
            return l;
        }

        double[] thirdShare;

        double thirdPoll(Camp k) { return thirdShare == null || k.idx >= thirdShare.length ? 0 : thirdShare[k.idx]; }

        void log(int kind, String text) { news.add(new News(week, kind, text)); }

        void log(int kind, int state, String text) { News n = new News(week, kind, text); n.state = state; news.add(n); }
    }

    // ---- calendar --------------------------------------------------------------------------------------------
    static final String[] MONTHS = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
    static final String[] DAYS = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};

    /** Calendar date of a day index (day 0 = Monday 9 November 2026): {year, month 0-11, day}. */
    static int[] dateOf(int day) {
        java.util.GregorianCalendar g = new java.util.GregorianCalendar(2026, 10, 9);
        g.add(java.util.Calendar.DAY_OF_MONTH, day);
        return new int[]{g.get(java.util.Calendar.YEAR), g.get(java.util.Calendar.MONTH), g.get(java.util.Calendar.DAY_OF_MONTH)};
    }

    static String dateStr(int day) { int[] d = dateOf(day); return MONTHS[d[1]] + " " + d[2] + ", " + d[0]; }

    static String weekStr(int week) { int[] a = dateOf(week * 7), b = dateOf(week * 7 + 6); return MONTHS[a[1]] + " " + a[2] + " - " + (a[1] == b[1] ? "" : MONTHS[b[1]] + " ") + b[2] + ", " + b[0]; }

    /** Week index of a calendar date. */
    static int weekOf(int year, int month0, int day) {
        java.util.GregorianCalendar a = new java.util.GregorianCalendar(2026, 10, 9), b = new java.util.GregorianCalendar(year, month0, day);
        long days = (b.getTimeInMillis() - a.getTimeInMillis() + 43200000L) / 86400000L;
        return (int) Math.floor(days / 7.0);
    }

    // =====================================================================
    //  8.  THE ELECTORATE  -  96 voter cells in every state, calibrated to 2020-2024
    // =====================================================================
    static final class Elect {
        static final double NAT24 = 0.4924, NAT20 = 0.5226, NAT22 = 0.4864;   // national two-party Democratic share
        static double[][] cvap = new double[NS][NCELL];       // eligible voters, 2028 composition
        static double[][] cvap24 = new double[NS][NCELL];     // eligible voters, 2024 composition
        static double[][] baseL = new double[NS][NCELL];      // calibrated Democratic logit (2024 environment)
        static double[][] turnB = new double[NS][NCELL];      // base turnout
        static double[][][] ov = new double[NS][NCELL][NO];   // overlay fractions
        static double[][][] ideal = new double[NS][NCELL][NI];
        static double[][][] sal = new double[NS][NCELL][NI];
        static double[][] elast = new double[NS][NCELL];
        static double[] offset = new double[NS], turnScale = new double[NS], target = new double[NS];
        static double[] blocK = new double[NB];
        static double[][] ageSexK = new double[NAGE][NSEX];
        /** 2024 two-party Democratic share by age and sex (AP VoteCast / Pew validated voters). */
        static final double[][] AGESEX_D24 = {{0.42, 0.59}, {0.425, 0.525}, {0.44, 0.52}};
        static double[] stateVotes24 = new double[NS];
        static double[] lean = new double[NS];                // blended D-minus-R lean relative to the nation (points)
        static double[][] dmaShare;                           // [market][state] share of the state's TV households
        static String[] marketName;
        static double[] marketHH;
        static int[][] marketStates;
        static int NM;
        static boolean ready;

        /** Small swings since 2024 seen in 2025-26 elections and polling (logit): Hispanic and young voters drifting back. */
        static double swing2026(int c) {
            int b = cellBloc(c), a = cellAge(c);
            double v = 0;
            if (hispanic(b)) v += 0.11;
            if (b == B_BLK) v += 0.04;
            if (b == B_ASN) v += 0.06;
            if (a == AGE_YOUNG) v += 0.08;
            if (b == B_WOT_C || b == B_WCA_C) v += 0.03;
            return v;
        }

        static synchronized void init() {
            if (ready) return;
            for (int s = 0; s < NS; s++) compose(s);
            calibrate();
            markets();
            ready = true;
        }

        static double[] blocShares(StateDef d, int years) {
            double W = d.white, B = d.black, H = d.hisp;
            double A = d.asian, N = d.nativeAm, O = Math.max(0.6, d.other - A - N);
            if (d.ab.equals("HI")) { A = Math.min(A, d.other - 1); O = Math.max(2, d.other - A - N); }
            W *= Math.pow(1 - 0.0075, years); H *= Math.pow(1.02, years); A *= Math.pow(1.022, years); O *= Math.pow(1.03, years); B *= Math.pow(1.002, years);
            // share of each group that is an adult citizen
            double wc = W * 0.80, bc = B * 0.70, hc = H * 0.43, ac = A * 0.55, nc = N * 0.66, oc = O * 0.50;
            double tot = wc + bc + hc + ac + nc + oc;
            wc /= tot; bc /= tot; hc /= tot; ac /= tot; nc /= tot; oc /= tot;
            double adj = 1.05;
            double wev = Math.min(0.55 * wc, d.wev / 100 * adj), wca = Math.min(0.5 * wc, d.wca / 100 * adj);
            double jew = Math.min(0.2 * wc, d.jew / 100 * 1.1), lds = Math.min(0.75 * wc, d.lds / 100 * 0.9 * adj), arb = Math.min(0.1 * wc, d.arab / 100);
            double wot = wc - wev - wca - jew - lds - arb;
            if (wot < 0.05 * wc) {
                double f = (wc * 0.95) / (wev + wca + jew + lds + arb);
                wev *= f; wca *= f; jew *= f; lds *= f; arb *= f; wot = 0.05 * wc;
            }
            double cw = clamp(1 - d.nocolW / 100 + 0.012 * years, 0.12, 0.85);
            double fot = Math.min(0.85, cw + cw * 0.4 * wev / Math.max(0.01, wot));
            double[] sh = new double[NB];
            sh[B_WEV_NC] = wev * (1 - 0.6 * cw); sh[B_WEV_C] = wev * 0.6 * cw;
            sh[B_WCA_NC] = wca * (1 - cw); sh[B_WCA_C] = wca * cw;
            sh[B_WOT_NC] = wot * (1 - fot); sh[B_WOT_C] = wot * fot;
            sh[B_JEW] = jew; sh[B_LDS] = lds; sh[B_ARB] = arb;
            sh[B_BLK] = bc;
            sh[B_HCU] = hc * d.cuban; sh[B_HPR] = hc * d.pr; sh[B_HMX] = hc * (1 - d.cuban - d.pr);
            sh[B_ASN] = ac; sh[B_NAT] = nc; sh[B_OTH] = oc;
            double t = 0;
            for (double v : sh) t += v;
            for (int b = 0; b < NB; b++) sh[b] /= t;
            return sh;
        }

        static void compose(int s) {
            StateDef d = STATES[s];
            double grow = d.region.equals("S") || d.region.equals("W") ? 1.25 : 0.7;
            double cv24 = d.cvap * (1 + 0.007 * 8 * grow), cv28 = d.cvap * (1 + 0.007 * 12 * grow);
            double[] sh24 = blocShares(d, 10), sh28 = blocShares(d, 14);
            double fy = clamp(d.u29 / 39.9, 0.7, 1.4), fo = clamp(d.o65 / 14.9, 0.6, 1.5);
            for (int b = 0; b < NB; b++) {
                double[] ag = {BLOC_AGE[b][0] * fy, BLOC_AGE[b][1], BLOC_AGE[b][2] * fo};
                double at = ag[0] + ag[1] + ag[2];
                for (int a = 0; a < NAGE; a++) {
                    double men = b == B_BLK ? 0.45 : b == B_ARB ? 0.52 : 0.48;
                    for (int x = 0; x < NSEX; x++) {
                        int c = cell(b, a, x);
                        double f = ag[a] / at * (x == 0 ? men : 1 - men);
                        cvap24[s][c] = cv24 * sh24[b] * f;
                        cvap[s][c] = cv28 * sh28[b] * f;
                        overlays(d, s, c, b, a, x);
                        opinions(s, c, b, a, x);
                        elast[s][c] = BLOC_ELAST[b] * AGE_ELAST[a];
                    }
                }
            }
        }

        static void overlays(StateDef d, int s, int c, int b, int a, int x) {
            double[] o = ov[s][c];
            double rb, ub;
            switch (b) {
                case B_WEV_NC: case B_WEV_C: rb = 1.5; ub = 0.4; break;
                case B_WCA_NC: case B_WCA_C: rb = 1.0; ub = 0.7; break;
                case B_WOT_NC: rb = 1.3; ub = 0.6; break;
                case B_WOT_C: rb = 0.75; ub = 1.25; break;
                case B_BLK: rb = 0.55; ub = 1.8; break;
                case B_HMX: rb = 0.45; ub = 1.5; break;
                case B_HCU: rb = 0.2; ub = 1.3; break;
                case B_HPR: rb = 0.3; ub = 1.8; break;
                case B_ASN: rb = 0.2; ub = 1.6; break;
                case B_NAT: rb = 2.0; ub = 0.5; break;
                case B_JEW: rb = 0.15; ub = 1.6; break;
                case B_LDS: rb = 1.0; ub = 0.6; break;
                case B_ARB: rb = 0.2; ub = 1.6; break;
                default: rb = 0.6; ub = 1.3; break;
            }
            double rural = clamp(d.rural / 100 * rb * (a == 0 ? 0.85 : a == 2 ? 1.15 : 1), 0, 0.92);
            double urban = clamp(d.urban / 100 * ub * (a == 0 ? 1.3 : a == 2 ? 0.8 : 1), 0, 0.97 - rural);
            o[O_RURAL] = rural; o[O_URBAN] = urban; o[O_SUBURB] = Math.max(0, 1 - rural - urban);
            boolean col = college(b);
            o[O_UNION] = clamp(d.union / 100 * (col ? 0.8 : 1.15) * (b == B_BLK ? 1.3 : hispanic(b) ? 0.9 : 1) * (a == 0 ? 0.6 : a == 1 ? 1.1 : 1.0), 0, 0.7);
            o[O_VET] = clamp(d.vet / 100 * 1.4 * (x == 0 ? 1.8 : 0.35) * (a == 0 ? 0.4 : a == 1 ? 1.0 : 1.8), 0, 0.6);
            double gb = b <= B_WEV_C ? 1.3 : b == B_WOT_NC ? 1.15 : b == B_WOT_C ? 0.75 : b == B_WCA_NC || b == B_WCA_C ? 1.0 : b == B_LDS ? 1.1 : b == B_NAT ? 1.0
                    : b == B_JEW || b == B_ARB ? 0.3 : b == B_ASN ? 0.4 : 0.6;
            o[O_GUN] = clamp(d.gun / 100 * gb * (x == 0 ? 1.25 : 0.8) * (0.8 + rural * 0.6), 0, 0.92);
            o[O_LGBT] = clamp(d.lgbt / 100 * (a == 0 ? 2.6 : a == 1 ? 0.75 : 0.3) * (b <= B_WEV_C ? 0.3 : b == B_LDS ? 0.4 : 1), 0, 0.4);
            o[O_STUDENT] = a == 0 ? (col ? 0.55 : 0.28) : 0;
            o[O_PARENT] = a == 1 ? 0.48 : a == 0 ? 0.15 : 0.02;
        }

        static void opinions(int s, int c, int b, int a, int x) {
            double p = BLOC_D24[b];
            double[] id = ideal[s][c], sl = sal[s][c];
            double tot = 0;
            for (int i = 0; i < NI; i++) {
                double v = IDEAL_R[i] + (IDEAL_D[i] - IDEAL_R[i]) * p + blocIdealShift(b, i) + AGE_IDEAL[a][i] + SEX_IDEAL[x][i];
                double m = ISSUE_SAL[i] * blocSalMul(b, i) * AGE_SAL[a][i] * SEX_SAL[x][i];
                for (int o = 0; o < NO; o++) {
                    double f = ov[s][c][o];
                    v += f * OV_IDEAL[o][i];
                    m *= 1 + f * (OV_SAL[o][i] - 1);
                }
                id[i] = clamp(v, -1, 1);
                sl[i] = m;
                tot += m;
            }
            for (int i = 0; i < NI; i++) sl[i] /= tot;
        }

        static double cellLogitRaw(int s, int c) {
            int b = cellBloc(c), a = cellAge(c), x = cellSex(c);
            double mean = (ageSexK[a][0] + ageSexK[a][1]) / 2;
            double adj = mean + (ageSexK[a][x] - mean) * BLOC_GENDER[b];
            double l = blocK[b] + adj;
            for (int o = 0; o < NO; o++) l += ov[s][c][o] * OV_LEAN[o];
            return l;
        }

        static void calibrate() {
            double[] nat = {0, 0, 0};
            for (int s = 0; s < NS; s++) {
                StateDef d = STATES[s];
                double sh24 = d.d24 / (d.d24 + d.r24), sh20 = d.d20 / (d.d20 + d.r20), sh22 = clamp(0.5 + d.m22 / 200, 0.05, 0.95);
                double rel = 0.65 * (sh24 - NAT24) + 0.25 * (sh20 - NAT20) + 0.10 * (sh22 - NAT22);
                target[s] = clamp(NAT24 + rel, 0.03, 0.97);
                lean[s] = rel * 200;
                stateVotes24[s] = d.t24;
            }
            for (int b = 0; b < NB; b++) blocK[b] = logit(BLOC_D24[b]);
            for (int a = 0; a < NAGE; a++) for (int x = 0; x < NSEX; x++) ageSexK[a][x] = AGESEX_ADJ[a][x];
            // targets: the national result must equal 2024 exactly (the blend shifts relative positions only)
            double tw0 = 0, tv0 = 0;
            for (int s = 0; s < NS; s++) { tw0 += stateVotes24[s] * target[s]; tv0 += stateVotes24[s]; }
            double shift = NAT24 - tw0 / tv0;
            for (int s = 0; s < NS; s++) { target[s] = clamp(target[s] + shift, 0.03, 0.97); lean[s] = (target[s] - NAT24) * 200; }
            // turnout scale per state to reproduce 2024 total votes
            for (int s = 0; s < NS; s++) {
                double lo = 0.3, hi = 2.0;
                for (int it = 0; it < 40; it++) {
                    double mid = (lo + hi) / 2, v = 0;
                    for (int c = 0; c < NCELL; c++) v += cvap24[s][c] * Math.min(0.97, baseTurn(c) * mid);
                    if (v > stateVotes24[s]) hi = mid; else lo = mid;
                }
                turnScale[s] = (lo + hi) / 2;
                for (int c = 0; c < NCELL; c++) turnB[s][c] = Math.min(0.97, baseTurn(c) * turnScale[s]);
            }
            // raking: state offsets reproduce the blended state results, bloc intercepts reproduce national bloc votes
            for (int pass = 0; pass < 30; pass++) {
                for (int s = 0; s < NS; s++) {
                    double lo = -4, hi = 4;
                    for (int it = 0; it < 40; it++) {
                        double mid = (lo + hi) / 2, dv = 0, tv = 0;
                        for (int c = 0; c < NCELL; c++) {
                            double w = cvap24[s][c] * turnB[s][c];
                            dv += w * logistic(cellLogitRaw(s, c) + mid * BLOC_STATE_W[cellBloc(c)]);
                            tv += w;
                        }
                        if (dv / tv > target[s]) hi = mid; else lo = mid;
                    }
                    offset[s] = (lo + hi) / 2;
                }
                double[] dv = new double[NB], tv = new double[NB];
                for (int s = 0; s < NS; s++)
                    for (int c = 0; c < NCELL; c++) {
                        int b = cellBloc(c);
                        double w = cvap24[s][c] * turnB[s][c];
                        dv[b] += w * logistic(cellLogitRaw(s, c) + offset[s] * BLOC_STATE_W[b]);
                        tv[b] += w;
                    }
                for (int b = 0; b < NB; b++) blocK[b] += 0.7 * (logit(BLOC_D24[b]) - logit(dv[b] / tv[b]));
                // age and sex
                double[][] ad = new double[NAGE][NSEX], at = new double[NAGE][NSEX];
                for (int s = 0; s < NS; s++)
                    for (int c = 0; c < NCELL; c++) {
                        double w = cvap24[s][c] * turnB[s][c];
                        ad[cellAge(c)][cellSex(c)] += w * logistic(cellLogitRaw(s, c) + offset[s] * BLOC_STATE_W[cellBloc(c)]);
                        at[cellAge(c)][cellSex(c)] += w;
                    }
                for (int a = 0; a < NAGE; a++) for (int x = 0; x < NSEX; x++) ageSexK[a][x] += 0.7 * (logit(AGESEX_D24[a][x]) - logit(ad[a][x] / at[a][x]));
            }
            for (int s = 0; s < NS; s++)
                for (int c = 0; c < NCELL; c++) baseL[s][c] = cellLogitRaw(s, c) + offset[s] * BLOC_STATE_W[cellBloc(c)];
        }

        static double baseTurn(int c) {
            int b = cellBloc(c), a = cellAge(c), x = cellSex(c);
            return BLOC_TURN[b] * AGE_TURN[a] * (x == 1 ? 1.03 : 0.97);
        }

        /** Builds the TV market table: real DMAs plus a 'rest of state' market where coverage is incomplete. */
        static void markets() {
            List<String> names = new ArrayList<String>();
            List<Double> hh = new ArrayList<Double>();
            List<double[]> shares = new ArrayList<double[]>();
            double[] cov = new double[NS];
            for (int m = 0; m < DMA_NAMES.length; m++) {
                double[] sh = new double[NS];
                for (String p : DMA_STATES[m].split(" ")) {
                    String[] kv = p.split(":");
                    int s = stateIdx(kv[0]);
                    double h = DMA_HH[m] * Double.parseDouble(kv[1]);
                    sh[s] = h / STATE_TVHH[s];
                    cov[s] += sh[s];
                }
                names.add(DMA_NAMES[m]); hh.add(DMA_HH[m]); shares.add(sh);
            }
            for (int s = 0; s < NS; s++) {
                if (cov[s] > 1) for (double[] sh : shares) sh[s] /= cov[s];
                else if (cov[s] < 0.93) {
                    double[] sh = new double[NS];
                    sh[s] = 1 - cov[s];
                    names.add("Rest of " + STATES[s].name);
                    hh.add(STATE_TVHH[s] * (1 - cov[s]));
                    shares.add(sh);
                }
            }
            NM = names.size();
            marketName = names.toArray(new String[0]);
            marketHH = new double[NM];
            dmaShare = new double[NM][];
            marketStates = new int[NM][];
            for (int m = 0; m < NM; m++) {
                marketHH[m] = hh.get(m);
                dmaShare[m] = shares.get(m);
                int k = 0;
                for (int s = 0; s < NS; s++) if (dmaShare[m][s] > 0.001) k++;
                marketStates[m] = new int[k];
                k = 0;
                for (int s = 0; s < NS; s++) if (dmaShare[m][s] > 0.001) marketStates[m][k++] = s;
            }
        }

        /** Markets covering a state, largest first. */
        static List<Integer> marketsIn(int s) {
            List<Integer> l = new ArrayList<Integer>();
            for (int m = 0; m < NM; m++) if (dmaShare[m][s] > 0.001) l.add(m);
            final int ss = s;
            Collections.sort(l, new Comparator<Integer>() { public int compare(Integer a, Integer b) { return Double.compare(dmaShare[b][ss], dmaShare[a][ss]); } });
            return l;
        }
    }

    // =====================================================================
    //  9.  VOTE CHOICE  -  general election and primaries
    // =====================================================================
    static final class Vote {
        static final double K_ISSUE = 0.30, K_CRED = 0.30, K_QUAL = 0.0024, K_APPEAL = 0.75;

        /** Membership of cell c of state s in voter group g (overlay groups use the overlay fraction). */
        static double member(int g, int s, int c) {
            int o = GROUP_OV[g];
            return o >= 0 ? Elect.ov[s][c][o] : inGroup(g, c);
        }

        static double groupTerm(int[] gs, double[] vs, int s, int c) {
            double v = 0;
            for (int i = 0; i < gs.length; i++) v += vs[i] * member(gs[i], s, c);
            return v;
        }

        /** A campaign's earned image with a group saturates: outreach can move a group a few points, not twenty. */
        static double imgSat(double x) { return 0.07 * Math.tanh(x / 0.07); }

        /** Net favourability shift saturates too (about +/-3 points of margin at most). */
        static double favSat(double x) { return 0.12 * Math.tanh(x / 0.12); }

        static double groupTerm(double[] img, int s, int c) {
            double v = 0;
            for (int g = 0; g < NG; g++) if (img[g] != 0) v += imgSat(img[g]) * member(g, s, c);
            return v;
        }

        /** Damage from attack lines that have stuck (each line saturates). */
        static double attackDamage(Camp k) {
            double v = 0;
            for (int a = 1; a < ATTACK.length; a++) v += 0.05 * Math.tanh(k.attackStick[a] / 1.2);
            return v;
        }

        /** How much closer to the party base the candidate's platform is than a generic nominee (positive = more orthodox). */
        static double baseAlignment(Camp k) {
            if (k.party > REP) return 0;
            double[] ideal = k.party == DEM ? IDEAL_D : IDEAL_R, gen = k.party == DEM ? GENERIC_D : GENERIC_R;
            double v = 0;
            for (int i = 0; i < NI; i++) v += ISSUE_SAL[i] * (Math.abs(gen[i] - ideal[i]) - Math.abs(k.plat[i] - ideal[i]));
            return v;
        }

        /** Base enthusiasm a nominee starts with: fire, orthodoxy and establishment unity. */
        static double baseEnthusiasm(Camp k) {
            return clamp(0.47 + 0.10 * (k.a(A_BAS) - 60) / 40.0 + 0.9 * baseAlignment(k) + 0.03 * (k.a(A_EST) - 60) / 40.0, 0.2, 0.85);
        }

        /** Overall candidate quality for general-election voters (relative to an average nominee). */
        static double quality(Game g, Camp k) {
            double war = g.set.scenario == SC_WAR ? 2.0 : 1.0;
            double q = 0.30 * (k.a(A_CHA) - 62) + 0.20 * (k.a(A_AUT) - 60) + 0.30 * (k.a(A_XOV) - 50) + 0.12 * (k.a(A_EXE) - 55)
                    + 0.12 * war * (k.a(A_FPC) - 50) + 0.10 * (k.a(A_DIS) - 65) + 0.06 * (k.a(A_INT) - 70);
            int age = 2028 - k.c.born;
            if (age > 72) q -= (age - 72) * 2.2;
            return q * K_QUAL;
        }

        /** Issue-position effect for a cell: how much better the candidate fits than the generic nominee of the party. */
        static double issueTerm(Game g, Camp k, int s, int c) {
            double[] id = Elect.ideal[s][c], sl = Elect.sal[s][c];
            double[] gen = k.party == DEM ? GENERIC_D : k.party == REP ? GENERIC_R : null;
            double v = 0;
            for (int i = 0; i < NI; i++) {
                double w = sl[i] * g.salience[i];
                double d = -Math.abs(k.plat[i] - id[i]);
                if (gen != null) d += Math.abs(gen[i] - id[i]);
                v += w * (d * K_ISSUE + k.cred[i] * K_CRED);
            }
            return v;
        }

        /** Distance of a cell from a platform (salience weighted). */
        static double distance(Game g, double[] plat, int s, int c) {
            double[] id = Elect.ideal[s][c], sl = Elect.sal[s][c];
            double v = 0;
            for (int i = 0; i < NI; i++) v += sl[i] * g.salience[i] * Math.abs(plat[i] - id[i]);
            return v;
        }

        /** Everything about the candidate (not the campaign) that moves a cell, relative to a generic nominee. */
        static double candTerm(Game g, Camp k, int s, int c) {
            double v = quality(g, k) + issueTerm(g, k, s, c) + K_APPEAL * groupTerm(k.c.apG, k.c.apV, s, c) + groupTerm(k.imageG, s, c) + favSat(k.favor);
            if (s == k.c.home) v += 0.025 + 0.055 * k.a(A_HOM) / 100.0;
            v -= k.flipflop * (1 - k.a(A_AUT) / 140.0) * 0.06;
            v -= k.scandalRevealed * 0.10;
            v -= attackDamage(k);
            v -= Campaign.selfFundPenalty(k, s, c);
            if (k.nominee) { Cand vp = g.vpOf(k.party); if (vp != null) v += vpTerm(g, vp, s, c); }
            return v;
        }

        static double vpTerm(Game g, Cand vp, int s, int c) {
            double v = 0.1 * groupTerm(vp.apG, vp.apV, s, c) + 0.00025 * (vp.a[A_XOV] - 50) + 0.0002 * (vp.a[A_FPC] - 50);
            if (s == vp.home) v += 0.02 + 0.035 * vp.a[A_HOM] / 100.0;
            v -= 0.02 * Math.max(0, vp.a[A_SCA] - 50) / 50.0;
            return v;
        }

        /** Campaign assets in the state (ads, digital, visits, field, allied PACs). */
        static double campTerm(Camp k, int s, int c) {
            if (k == null) return 0;
            double tv = 0.050 * Math.log(1 + (k.tvStock[s] + 0.75 * k.superPac[s]) / 350.0);
            double dg = 0.026 * Math.log(1 + k.digA[s][cellAge(c)] / 25.0);
            double vis = 0.022 * Math.log(1 + k.visit[s]);
            double fld = 0.9 * Week.fieldSat(k.persuade[s]);
            return tv + dg + vis + fld + 0.4 * k.localImage[s];
        }

        /** Democratic logit for a cell in a two-way race, including environment, candidates and campaigns. */
        static double cellLogit(Game g, Camp d, Camp r, int s, int c) {
            double e = Elect.elast[s][c];
            double l = Elect.baseL[s][c] + Elect.swing2026(c) + e * g.env / 50.0;
            double cand = (d != null ? candTerm(g, d, s, c) : 0) - (r != null ? candTerm(g, r, s, c) : 0);
            double camp = campTerm(d, s, c) - campTerm(r, s, c);
            return l + e * (cand + camp);
        }

        static final double[] THIRD_BASE = {0, 0, 0.011, 0.012, 0.009, 0.15, 0.05};

        /**
         * A serious independent's share of a cell: issue fit against the nearer major candidate, the candidate and the
         * campaign, name recognition and viability (nobody wants to waste a vote), and how open the voters are to a third choice.
         */
        static double indShare(Game g, Camp t, Camp d, Camp r, int s, int c, double distD, double distR, double natShare) {
            double dt = distance(g, t.plat, s, c);
            double near = Math.min(distD, distR);
            double u = -2.0 + 5.4 * (near - dt);
            u += (quality(g, t) + K_APPEAL * groupTerm(t.c.apG, t.c.apV, s, c) + groupTerm(t.imageG, s, c) + favSat(t.favor)) * 2.0;
            u -= attackDamage(t) * 2 + t.scandalRevealed * 0.2 + t.flipflop * 0.1;
            u += campTerm(t, s, c) * 2.4;
            u += Math.log(Math.max(0.05, t.nameIdS[s])) * 0.9;
            u += 2.4 * (Math.min(0.4, natShare) - 0.12);
            u += (Elect.elast[s][c] - 1) * 0.8 + (cellAge(c) == AGE_YOUNG ? 0.25 : 0);
            double p = logistic(Elect.baseL[s][c] + Elect.swing2026(c));
            u -= 1.6 * Math.abs(p - 0.5);
            if (g.set.scenario == SC_THIRD) u += 0.7;
            if (s == t.c.home) u += 0.3 + 0.5 * t.a(A_HOM) / 100.0;
            Cand vp = g.vpOf(t.party);
            if (vp != null && t.party == IND) u += 2.0 * vpTerm(g, vp, s, c);
            double sh = logistic(u);
            if (t.onBallot != null && !t.onBallot[s]) sh *= 0.12;
            return Math.min(0.85, sh);
        }

        /** Third-party share of a cell. */
        static double thirdShare(Game g, Camp t, Camp d, Camp r, int s, int c, double distD, double distR) {
            if (t == null || !t.active) return 0;
            double base = THIRD_BASE[t.party] * (0.5 + 0.5 * t.enthusiasm * 2) * (1 + t.momentum);
            if (g.set.scenario == SC_THIRD && t.party == AMP) base *= 1.15;
            double dt = distance(g, t.plat, s, c);
            double near = Math.min(distD, distR);
            double fit = logistic(9 * (near - dt) + 0.4);
            int b = cellBloc(c), a = cellAge(c), x = cellSex(c);
            double m = 1;
            if (t.party == GRN) { if (b == B_ARB) m *= 5; if (a == 0) m *= 2.2; if (b == B_WOT_C) m *= 1.2; }
            if (t.party == LBT) { if (a == 0 && x == 0) m *= 2.2; if (b == B_WOT_NC || b == B_WOT_C) m *= 1.2; }
            if (t.party == FWD) { if (a == 0) m *= 1.7; if (b == B_ASN) m *= 1.6; }
            if (t.party == AMP) { m *= 0.6 + Elect.elast[s][c] * 0.5; if (a == 0 && x == 0) m *= 1.3; }
            double share = base * m * fit * 2;
            if (STATES[s].ab.equals("DC") && t.party != AMP) share *= 0.5;
            return Math.min(0.6, share);
        }

        /** Computes the general election in every state. out[s] = {D votes, R votes, third votes, turnout}. thirdOut[k] totals. */
        /** Turnout of a cell given the D-vs-R preference p and the independent's share (the field programs and enthusiasm matter). */
        static double turnout(Game g, Camp d, Camp r, Camp ind, int s, int c, double p, double indSh) {
            double enD = d != null ? d.enthusiasm : 0.5, enR = r != null ? r.enthusiasm : 0.5;
            double turn = Elect.turnB[s][c] * (1 + 0.16 * (enD - 0.5) * 2 * p + 0.16 * (enR - 0.5) * 2 * (1 - p));
            if (d != null) turn += Week.gotvSat(d.gotv[s]) * p * (1 - turn);
            if (r != null) turn += Week.gotvSat(r.gotv[s]) * (1 - p) * (1 - turn);
            if (ind != null && indSh > 0) turn += Week.gotvSat(ind.gotv[s]) * indSh * (1 - turn) + 0.15 * indSh * (ind.enthusiasm - 0.4) * (1 - turn);
            return clamp(turn * g.turnoutMood(), 0.05, 0.97);
        }

        static double[][] general(Game g, double[][] out, double[] thirdVotes) {
            Camp d = g.genCand(DEM), r = g.genCand(REP);
            List<Camp> thirds = new ArrayList<Camp>();
            for (Camp k : g.camps) if (k.party >= GRN && k.active && (k.nominee || k.party == IND)) thirds.add(k);
            Camp ind = g.indep();
            double indNat = ind != null ? g.thirdPoll(ind) : 0;
            if (out == null || out.length != NS || out[0].length < 5) out = new double[NS][5];
            if (thirdVotes != null) Arrays.fill(thirdVotes, 0);
            for (int s = 0; s < NS; s++) {
                double vd = 0, vr = 0, vt = 0, vi = 0, tot = 0;
                for (int c = 0; c < NCELL; c++) {
                    double n = Elect.cvap[s][c];
                    if (n < 1) continue;
                    double p = logistic(cellLogit(g, d, r, s, c));
                    double third = 0, indSh = 0;
                    if (!thirds.isEmpty()) {
                        double dD = d != null ? distance(g, d.plat, s, c) : 0.4, dR = r != null ? distance(g, r.plat, s, c) : 0.4;
                        for (Camp t : thirds) {
                            double ts = t.party == IND ? indShare(g, t, d, r, s, c, dD, dR, indNat) : thirdShare(g, t, d, r, s, c, dD, dR);
                            third += ts;
                            if (t == ind) indSh = ts;
                            if (thirdVotes != null) thirdVotes[t.idx] += ts;
                        }
                        if (third > 0.9) { indSh *= 0.9 / third; third = 0.9; }
                    }
                    double v = n * turnout(g, d, r, ind, s, c, p, indSh);
                    vd += v * (1 - third) * p;
                    vr += v * (1 - third) * (1 - p);
                    vt += v * third;
                    vi += v * indSh;
                    tot += v;
                }
                out[s][0] = vd; out[s][1] = vr; out[s][2] = vt; out[s][3] = tot; out[s][4] = vi;
            }
            if (thirdVotes != null) {
                // rescale the per-cell accumulation into vote shares weighted by turnout (approximation for display)
                double s3 = 0;
                for (int s = 0; s < NS; s++) s3 += out[s][2];
                double acc = 0;
                for (Camp t : thirds) acc += thirdVotes[t.idx];
                for (Camp t : thirds) thirdVotes[t.idx] = acc > 0 ? s3 * thirdVotes[t.idx] / acc : 0;
            }
            return out;
        }

        /** Margin (D minus R, points of the total vote) in a state from a result row. */
        static double margin(double[] row) { double t = row[0] + row[1] + row[2]; return t <= 0 ? 0 : (row[0] - row[1]) / t * 100; }

        /** Electoral votes from per-state margins (including the ME and NE districts). Returns {D, R}. */
        static int[] electoral(double[] m, int[] cdWin) {
            int d = 0, r = 0;
            for (int s = 0; s < NS; s++) {
                int ev = STATES[s].ev;
                boolean split = STATES[s].ab.equals("ME") || STATES[s].ab.equals("NE");
                if (split) ev = 2;
                if (m[s] > 0) d += ev; else r += ev;
            }
            for (int k = 0; k < CD_DATA.length; k++) {
                int s = (int) CD_DATA[k][0];
                StateDef st = STATES[s];
                double stateM24 = (st.d24 - st.r24) / st.t24 * 100;
                double cm = m[s] + (CD_DATA[k][2] - stateM24);
                boolean dw = cm > 0;
                if (cdWin != null) cdWin[k] = dw ? DEM : REP;
                if (dw) d++; else r++;
            }
            return new int[]{d, r};
        }

        /**
         * Electoral votes when an independent may carry states: rows are {D, R, all third-party, ..., independent at [ind]}.
         * Returns {D, R, I}; cdWin gets DEM, REP or IND for each congressional district (ME and NE).
         */
        static int[] electoral3(double[][] rows, int ind, int[] cdWin) {
            int d = 0, r = 0, in = 0;
            for (int s = 0; s < NS; s++) {
                double[] row = rows[s];
                double vi = ind < row.length ? row[ind] : 0;
                int ev = STATES[s].ev;
                boolean split = STATES[s].ab.equals("ME") || STATES[s].ab.equals("NE");
                if (split) ev = 2;
                int w = winner(row[0], row[1], vi);
                if (w == DEM) d += ev; else if (w == REP) r += ev; else in += ev;
            }
            for (int k = 0; k < CD_DATA.length; k++) {
                int s = (int) CD_DATA[k][0];
                double[] row = rows[s];
                double tot = Math.max(1, row[0] + row[1] + row[2]);
                StateDef st = STATES[s];
                double shift = (CD_DATA[k][2] - (st.d24 - st.r24) / st.t24 * 100) / 200.0 * tot;
                double vi = ind < row.length ? row[ind] : 0;
                int w = winner(row[0] + shift, row[1] - shift, vi);
                if (cdWin != null) cdWin[k] = w;
                if (w == DEM) d++; else if (w == REP) r++; else in++;
            }
            return new int[]{d, r, in};
        }

        static int winner(double d, double r, double i) { return i > d && i > r ? IND : d >= r ? DEM : REP; }

        /** District margin implied by a statewide margin. */
        static double cdMargin(int k, double stateMargin) {
            StateDef st = STATES[(int) CD_DATA[k][0]];
            return stateMargin + (CD_DATA[k][2] - (st.d24 - st.r24) / st.t24 * 100);
        }

        /** Support of a voter group (D minus R points) in a state (or nationally when s < 0). */
        static double groupMargin(Game g, int grp, int s) {
            Camp d = g.genCand(DEM), r = g.genCand(REP);
            double dv = 0, rv = 0;
            int s0 = s < 0 ? 0 : s, s1 = s < 0 ? NS : s + 1;
            int o = GROUP_OV[grp];
            for (int st = s0; st < s1; st++)
                for (int c = 0; c < NCELL; c++) {
                    double w = member(grp, st, c);
                    if (w <= 0) continue;
                    double n = Elect.cvap[st][c] * Elect.turnB[st][c] * w;
                    double l = cellLogit(g, d, r, st, c);
                    if (o >= 0) l += OV_LEAN[o] * (1 - Elect.ov[st][c][o]);
                    double p = logistic(l);
                    dv += n * p; rv += n * (1 - p);
                }
            return dv + rv <= 0 ? 0 : (dv - rv) / (dv + rv) * 100;
        }

        /** Share of the electorate (in a state or nationally) belonging to a group. */
        static double groupSize(int grp, int s) {
            double w = 0, t = 0;
            int s0 = s < 0 ? 0 : s, s1 = s < 0 ? NS : s + 1;
            for (int st = s0; st < s1; st++)
                for (int c = 0; c < NCELL; c++) {
                    double n = Elect.cvap[st][c] * Elect.turnB[st][c];
                    w += n * member(grp, st, c);
                    t += n;
                }
            return t <= 0 ? 0 : w / t;
        }

        // ---------------------------------------------------------------- PRIMARIES
        static double PRIMARY_TEMP = 0.7, P_NAME = 1.8, P_ATTR = 2.0;

        /** Weight of a cell in a party's primary electorate. */
        static double primaryWeight(int party, int s, int c) {
            double p = logistic(Elect.baseL[s][c] + Elect.swing2026(c));
            double ps = party == DEM ? p : 1 - p;
            int a = cellAge(c), b = cellBloc(c);
            double t = Elect.cvap[s][c] * Elect.turnB[s][c] * Math.pow(ps, 1.7) * (a == 0 ? 0.55 : a == 1 ? 1.0 : 1.4) * (college(b) ? 1.15 : 1.0);
            return t;
        }

        /** Primary-voter issue ideal: the partisan ideal adjusted for bloc, age, sex and overlays. */
        static double primaryIdeal(int party, int s, int c, int i) {
            int b = cellBloc(c), a = cellAge(c), x = cellSex(c);
            double v = (party == DEM ? IDEAL_D[i] : IDEAL_R[i]) + blocIdealShift(b, i) * 0.7 + AGE_IDEAL[a][i] * 1.2 + SEX_IDEAL[x][i];
            for (int o = 0; o < NO; o++) v += Elect.ov[s][c][o] * OV_IDEAL[o][i];
            return clamp(v, -1, 1);
        }

        static double[][][] pIdealCache = new double[2][][];

        static double primaryIdealCached(int party, int s, int c, int i) {
            if (pIdealCache[party] == null) {
                double[][] t = new double[NS * NCELL][NI];
                for (int ss = 0; ss < NS; ss++) for (int cc = 0; cc < NCELL; cc++) for (int ii = 0; ii < NI; ii++) t[ss * NCELL + cc][ii] = primaryIdeal(party, ss, cc, ii);
                pIdealCache[party] = t;
            }
            return pIdealCache[party][s * NCELL + c][i];
        }

        /** Utility of a primary candidate for a cell. */
        static double primaryU(Game g, Camp k, int s, int c) {
            int party = k.party, a = cellAge(c), b = cellBloc(c);
            double u = clamp(k.momentum, -0.8, 1.2) * 0.7 + Math.log(Math.max(0.05, k.nameIdS[s])) * P_NAME + k.favor * 2;
            // ideology and issues
            double[] sl = Elect.sal[s][c];
            double fit = 0;
            for (int i = 0; i < NI; i++) fit -= sl[i] * g.salience[i] * Math.abs(k.plat[i] - primaryIdealCached(party, s, c, i));
            u += fit * 3.2;
            // the base wants a fighter; older and Black Democrats weigh electability and establishment backing
            double at = (k.a(A_BAS) - 60) * 0.014 * (a == 0 ? 1.3 : 1.0);
            double estW = party == DEM ? (b == B_BLK || a == AGE_OLD ? 0.012 : 0.005) : 0.006;
            at += (k.a(A_EST) - 60) * estW;
            at += (k.a(A_CHA) - 65) * 0.008 + (k.a(A_XOV) - 50) * (party == DEM ? 0.004 : 0.001);
            u += at * P_ATTR;
            if (party == REP && k.c.id.equals("vance")) u += 0.3;       // the sitting vice president's head start
            u += groupTerm(k.c.apG, k.c.apV, s, c) * 3.0 + groupTerm(k.imageG, s, c) * 1.5;
            if (s == k.c.home) u += 0.35 + 0.6 * k.a(A_HOM) / 100.0;
            else if (STATES[s].region.equals(STATES[Math.max(0, k.c.home)].region)) u += 0.08;
            // campaign effort in the state: retail, visits, ads, organisers
            u += 0.30 * Math.log(1 + k.visit[s]) + 0.12 * Math.log(1 + k.tvStock[s] / 200.0) + 0.07 * Math.log(1 + k.digA[s][a] / 30.0)
                    + 2.6 * Week.fieldSat(k.persuade[s]) + k.localImage[s] * 2.2;
            u -= k.scandalRevealed * 0.4 + k.flipflop * 0.12;
            u -= Campaign.selfFundPenalty(k, s, c) * 1.6;
            u -= 2 * attackDamage(k);
            if (party == REP && g.trumpEndorse == k.idx) u += 0.28 + (a == AGE_OLD ? 0.1 : 0) - (college(b) ? 0.15 : 0);
            for (int e : k.endorsements) u += ENDORSERS[e].weight * member(ENDORSERS[e].group, s, c) * 3;
            return u + k.pInt + k.drift + k.inherit;
        }

        /** Primary vote shares in a state. Returns shares indexed by camp idx (0 for others). */
        static double[] primaryShares(Game g, int party, int s, double[] out) {
            int n = g.camps.size();
            if (out == null || out.length < n) out = new double[n];
            Arrays.fill(out, 0);
            List<Camp> cs = new ArrayList<Camp>();
            for (Camp k : g.camps) if (k.party == party && k.active) cs.add(k);
            if (cs.isEmpty()) return out;
            double[] u = new double[cs.size()];
            double tw = 0;
            for (int c = 0; c < NCELL; c++) {
                double w = primaryWeight(party, s, c);
                if (w <= 0) continue;
                double mx = -1e9;
                for (int i = 0; i < cs.size(); i++) { u[i] = primaryU(g, cs.get(i), s, c); mx = Math.max(mx, u[i]); }
                double z = 0;
                for (int i = 0; i < cs.size(); i++) { u[i] = Math.exp((u[i] - mx) / PRIMARY_TEMP); z += u[i]; }
                for (int i = 0; i < cs.size(); i++) out[cs.get(i).idx] += w * u[i] / z;
                tw += w;
            }
            for (int i = 0; i < cs.size(); i++) out[cs.get(i).idx] /= tw;
            return out;
        }

        /** National primary poll (weighted by each state's primary electorate). */
        static double[] primaryNational(Game g, int party) {
            int n = g.camps.size();
            double[] tot = new double[n], tmp = new double[n];
            double tw = 0;
            for (int s = 0; s < NS; s++) {
                double w = 0;
                for (int c = 0; c < NCELL; c += 3) w += primaryWeight(party, s, c);
                primaryShares(g, party, s, tmp);
                for (int i = 0; i < n; i++) tot[i] += w * tmp[i];
                tw += w;
            }
            for (int i = 0; i < n; i++) tot[i] /= tw;
            return tot;
        }

        /** Sets each candidate's intercept so the opening national primary poll matches 2026 polling. */
        static void calibratePrimary(Game g, int party) {
            for (int it = 0; it < 14; it++) {
                double[] sh = primaryNational(g, party);
                double tgt = 0;
                for (Camp k : g.camps) if (k.party == party && k.active) tgt += k.c.poll;
                for (Camp k : g.camps) {
                    if (k.party != party || !k.active) continue;
                    double want = Math.max(0.3, k.c.poll) / tgt;
                    k.pInt += 0.55 * PRIMARY_TEMP * Math.log(want / Math.max(1e-4, sh[k.idx]));
                }
            }
            for (Camp k : g.camps) if (k.party == party) k.pInt0 = k.pInt;
        }

        /** How much of the opening-poll edge survives: early polls mostly measure name recognition and fade by the first contests. */
        static double pollEdgeFade(Game g, int party) {
            int first = Primary.firstContest(g, party);
            if (first >= 999) return 0;
            double w = g.week + (g.day >= 0 ? g.day / 7.0 : 0);
            if (w < first) return 1 - 0.9 * Math.pow(w / (double) first, 0.85);
            return 0.1 * Math.exp(-(w - first) / 6.0);
        }
    }

    // =====================================================================
    //  10.  NEW GAME: candidates, calendar, delegates, Senate map
    // =====================================================================
    static final class Setup {
        static Game newGame(Settings set) {
            Elect.init();
            Game g = new Game();
            g.set = set;
            g.rng = new Random(set.seed);
            double[] st = SCEN_START[set.scenario];
            g.env = st[0];
            g.approval = st[1]; g.unemp = st[2]; g.inflation = st[3]; g.gdp = st[4];
            g.houseD = set.midHouseD; g.senateD = set.midSenateD; g.demHouse = set.midHouseD >= 218;
            for (int i = 0; i < NI; i++) g.salience[i] = 1;
            scenarioSalience(g);
            // systematic polling error for this cycle
            g.natErr = g.rng.nextGaussian() * 2.2;
            for (int s = 0; s < NS; s++) g.stateErr[s] = g.rng.nextGaussian() * 1.6;
            for (int i = 0; i < g.houseEffect.length; i++) g.houseEffect[i] = g.rng.nextGaussian() * 1.6;
            for (int s = 0; s < NS; s++) g.senateNoise[s] = g.rng.nextGaussian() * 3.0;
            if (set.scenario == SC_WAR) g.warStart = 22;
            g.week = set.mode == MODE_FULL ? 0 : GENERAL_START_WEEK;
            Cand mine = set.custom != null ? set.custom : candById(set.candId);
            boolean ind = mine.party == IND;
            if (set.mode == MODE_FULL) {
                g.phase = PH_INVISIBLE;
                for (Cand c : ROSTER) if ((c.party == DEM || c.party == REP) && (c == mine || !set.sitOut.contains(c.id) || c.id.equals(set.lockOf(c.party)))) addCamp(g, c, c == mine);
                if (set.custom != null && !ind) addCamp(g, mine, true);
                Vote.calibratePrimary(g, DEM);
                Vote.calibratePrimary(g, REP);
                calendar(g);
                primaryDebates(g);
                for (Camp k : g.camps) if (!k.human) k.announced = false;
                if (ind) {
                    // the independent launches when the primaries begin (see prestart); until then the parties' race runs without them
                    Camp me = addCamp(g, mine, true);
                    me.nominee = true; me.announced = true; me.active = false;
                    me.onBallot = new boolean[NS];
                }
            } else if (ind) {
                g.phase = PH_GENERAL;
                Camp me = addCamp(g, mine, true);
                me.nominee = true; me.announced = true;
                me.onBallot = new boolean[NS];
                Arrays.fill(me.onBallot, true);
                Cand dc = set.lockD != null ? candById(set.lockD) : likelyNominee(DEM), rc = set.lockR != null ? candById(set.lockR) : likelyNominee(REP);
                Camp d = addCamp(g, dc, false), r = addCamp(g, rc, false);
                d.nominee = true; d.announced = true; r.nominee = true; r.announced = true;
                g.dNom = d.idx; g.rNom = r.idx;
                generalMoney(g, d);
                generalMoney(g, r);
                indMoney(g, me, true);
                for (Camp k : new Camp[]{d, r}) { for (int s = 0; s < NS; s++) k.nameIdS[s] = Math.max(k.nameIdS[s], 0.9); NameId.recompute(k); }
                addThirdParties(g);
                g.setVp(DEM, Primary.aiPickVp(g, d));
                g.setVp(REP, Primary.aiPickVp(g, r));
                g.pending.add(new Object[]{EV_VP, "Choose a running mate", "", new String[0], -1});
                General.setupDebates(g);
                g.debateTalks = 2;
            } else {
                g.phase = PH_GENERAL;
                Camp me = addCamp(g, mine, true);
                me.nominee = true;
                me.announced = true;
                Cand oc = set.opponentId != null ? candById(set.opponentId) : null;
                if (oc == null || oc.party == mine.party) oc = likelyNominee(mine.party == DEM ? REP : DEM);
                Camp op = addCamp(g, oc, false);
                op.nominee = true; op.announced = true;
                if (me.party == DEM) { g.dNom = me.idx; g.rNom = op.idx; } else { g.rNom = me.idx; g.dNom = op.idx; }
                generalMoney(g, me);
                generalMoney(g, op);
                for (Camp k : new Camp[]{me, op}) { for (int s = 0; s < NS; s++) k.nameIdS[s] = Math.max(k.nameIdS[s], 0.9); NameId.recompute(k); }
                addThirdParties(g);
                g.setVp(op.party, Primary.aiPickVp(g, op));
                g.pending.add(new Object[]{EV_VP, "Choose a running mate", "", new String[0], -1});
                General.setupDebates(g);
            }
            senate(g);
            g.player = 0;
            for (Camp k : g.camps) if (k.human) g.player = k.idx;
            World.environment(g);
            Polls.weekly(g);
            String sc = SCEN_NAME[set.scenario];
            g.log(NK_BIG, "Campaign headquarters opens. Scenario: " + sc + ". " + SCEN_TAG[set.scenario] + ".");
            if (set.mode == MODE_FULL)
                g.log(NK_INFO, "Midterms: Democrats won " + set.midHouseD + " House seats and hold " + set.midSenateD + " Senate seats; national House vote " + margin(set.midGeneric) + ".");
            return g;
        }

        /** Resources for an independent campaign: modest unless the scenario hands them a billionaire's backing. */
        static void indMoney(Game g, Camp k, boolean laborDay) {
            double cash = laborDay ? 30e6 + 90e6 * k.a(A_BIG) / 100 + 60e6 * k.a(A_SMD) / 100 : 15e6 + 40e6 * k.a(A_BIG) / 100 + 20e6 * k.a(A_SMD) / 100;
            if (g.set.scenario == SC_THIRD) { cash += 200e6; k.momentum += 0.25; }
            if (cash > k.cash) Week.raise(k, cash - k.cash, RS_GROUPS);
            k.smallList = Math.max(k.smallList, (laborDay ? 1.0e6 + 3e6 * k.a(A_SMD) / 100 : 0.4e6 + 1.5e6 * k.a(A_SMD) / 100));
            k.enthusiasm = 0.55;
            if (laborDay) for (int s = 0; s < NS; s++) {
                double lean = Math.abs(Elect.lean[s] + 1.5);
                if (lean < 9) {
                    k.offices[s] = Math.round(1 + STATES[s].ev * 0.35);
                    k.organizers[s] = Math.round(k.offices[s] * 3);
                    k.volunteers[s] = k.organizers[s] * 30;
                }
            }
        }

        /**
         * The independent's long campaign begins when the primaries do: the parties' invisible primary is simulated first
         * (the player's campaign sits it out), then the player takes over.
         */
        static void prestart(Game g) {
            Camp me = g.me();
            int start = Math.min(Primary.firstContest(g, DEM), Primary.firstContest(g, REP)) - 1;
            while (g.week < start && g.phase != PH_OVER) {
                g.pending.clear();
                for (Debate d : Debates.thisWeek(g)) if (!d.done) { DebateRun r = Debates.start(g, d); r.myIdx = -1; while (!r.over) Debates.segment(g, r, -1); }
                Week.resolve(g);
            }
            g.pending.clear();
            me.active = true;
            indMoney(g, me, false);
            g.log(NK_BIG, me.name().toUpperCase() + " LAUNCHES AN INDEPENDENT CAMPAIGN FOR PRESIDENT. First task: get on the ballot in 50 states and DC.");
            g.dirty = true;
            Polls.weekly(g);
        }

        static Camp addCamp(Game g, Cand c, boolean human) {
            Camp k = new Camp(c, g.camps.size());
            k.human = human;
            k.tvGrp = new double[Elect.NM];
            k.tvTone = new int[Elect.NM];
            for (int i = 0; i < NI; i++) k.cred[i] = 0;
            for (int i = 0; i < c.recIssue.length; i++) if (c.recIssue[i] >= 0) k.cred[c.recIssue[i]] += c.recCred[i] * 0.5;
            k.enthusiasm = Vote.baseEnthusiasm(k);
            k.aggression = 0.35 + 0.4 * g.rng.nextDouble();
            k.location = Math.max(0, c.home);
            NameId.init(k);
            Digital.defaults(k);
            if (human) { k.announced = true; k.announceWeek = 0; }
            g.camps.add(k);
            return k;
        }

        static Cand likelyNominee(int party) {
            Cand best = null;
            for (Cand c : ROSTER) if (c.party == party && (best == null || c.poll > best.poll)) best = c;
            return best;
        }

        /** Money available when the general election begins (post-convention cash plus party coordination). */
        static void generalMoney(Game g, Camp k) {
            double base = 120e6 + 220e6 * k.a(A_BIG) / 100 + 120e6 * k.a(A_SMD) / 100;
            if (base > k.cash) Week.raise(k, base - k.cash, RS_PARTY);
            k.smallList = Math.max(k.smallList, 4e6 + 6e6 * k.a(A_SMD) / 100);
            k.enthusiasm = Vote.baseEnthusiasm(k);
            // a running start: offices and organisers in the battlegrounds
            for (int s = 0; s < NS; s++) {
                double lean = Math.abs(Elect.lean[s] + 1.5);
                if (lean < 9) {
                    double big = STATES[s].ev;
                    k.offices[s] = Math.round(2 + big * 0.9);
                    k.organizers[s] = Math.round(k.offices[s] * 4.5);
                    k.volunteers[s] = k.organizers[s] * 35;
                }
            }
        }

        static void addThirdParties(Game g) {
            String[] ids = {"stein", "libert", "yang", "amp"};
            for (int i = 0; i < 4; i++) {
                boolean on = g.set.third[i] || (i == 3 && g.set.scenario == SC_THIRD);
                if (i == 3 && g.humanInd()) on = false;          // the player is the independent force
                if (!on) continue;
                boolean exists = false;
                for (Camp k : g.camps) if (k.c.id.equals(ids[i])) exists = true;
                if (exists) continue;
                Camp k = addCamp(g, candById(ids[i]), false);
                k.nominee = true; k.announced = true;
                k.enthusiasm = 0.5;
                if (k.party == AMP) { k.cash = 250e6; k.momentum = g.set.scenario == SC_THIRD ? 0.25 : 0; }
            }
        }

        static void scenarioSalience(Game g) {
            switch (g.set.scenario) {
                case SC_SCANDAL: g.salience[I_DEMOC] = 2.4; break;
                case SC_WAR: g.salience[I_CHINA] = 1.6; g.salience[I_COL] = 1.1; break;
                case SC_AIREC: g.salience[I_AI] = 2.6; g.salience[I_JOBS] = 2.6; g.salience[I_COL] = 1.1; break;
                case SC_BOOM: g.salience[I_COL] = 0.7; g.salience[I_JOBS] = 0.7; g.salience[I_IMMIG] = 1.2; break;
                case SC_THIRD: g.salience[I_DEMOC] = 1.4; break;
                default: break;
            }
        }

        // ---------------------------------------------------------------- primary calendar and delegates
        static final String[][] COMMON_DATES = {
                {"2028-03-07", "AL AR CA CO MA MN NC OK TN TX UT VT ME"}, {"2028-03-14", "GA MS WA"}, {"2028-03-21", "AZ FL IL KS OH"},
                {"2028-03-28", "LA"}, {"2028-04-04", "CT NY RI WI DE"}, {"2028-04-25", "PA"}, {"2028-05-02", "IN"},
                {"2028-05-09", "NE WV MD"}, {"2028-05-16", "KY OR"}, {"2028-06-06", "MT NJ SD DC"}};
        static final String[][] DEM_ONLY = {{"2028-04-11", "HI AK WY"}, {"2028-05-23", "ID"}, {"2028-03-14", "ND"}, {"2028-03-28", "MO"},
                {"2028-03-07", "IA"}, {"2028-02-29", "VA"}, {"2028-02-22", "MI"}, {"2028-02-15", "NM"}};
        static final String[][] REP_ONLY = {{"2028-03-14", "HI"}, {"2028-03-07", "AK ID ND MO VA"}, {"2028-04-18", "WY"}, {"2028-06-06", "NM"},
                {"2028-02-29", "MI"}};
        static final String[] DEM_EARLY_DATES = {"2028-01-22", "2028-02-01", "2028-02-08", "2028-02-15", "2028-02-22", "2028-02-29"};
        static final String[] REP_EARLY_DATES = {"2028-01-17", "2028-01-25", "2028-02-08", "2028-02-26"};
        static final String WTA_REP = "FL AZ OH IL NJ DE MD DC WI NE MT SD IN WV KY PA NY";

        static int weekOfStr(String d) {
            String[] t = d.split("-");
            return weekOf(Integer.parseInt(t[0]), Integer.parseInt(t[1]) - 1, Integer.parseInt(t[2]));
        }

        static void calendar(Game g) {
            for (int party = DEM; party <= REP; party++) {
                int[] wk = new int[NS];
                Arrays.fill(wk, -1);
                for (String[] e : COMMON_DATES) for (String ab : e[1].split(" ")) wk[stateIdx(ab)] = weekOfStr(e[0]);
                for (String[] e : party == DEM ? DEM_ONLY : REP_ONLY) for (String ab : e[1].split(" ")) wk[stateIdx(ab)] = weekOfStr(e[0]);
                int[] early = party == DEM ? g.set.demEarly : g.set.repEarly;
                String[] dates = party == DEM ? DEM_EARLY_DATES : REP_EARLY_DATES;
                for (int i = 0; i < early.length && i < dates.length; i++) if (early[i] >= 0) wk[early[i]] = weekOfStr(dates[i]);
                for (int s = 0; s < NS; s++) {
                    if (wk[s] < 0) wk[s] = weekOfStr("2028-06-06");
                    Contest c = new Contest();
                    c.party = party; c.state = s; c.week = wk[s];
                    String ab = STATES[s].ab;
                    c.caucus = party == REP ? "IA NV ND WY ID MO AK HI".contains(ab) : "WY AK ND".contains(ab);
                    c.wta = party == REP && WTA_REP.contains(ab);
                    c.delegates = party == DEM ? demDelegates(s) : repDelegates(s);
                    c.label = STATES[s].name + (c.caucus ? " caucus" : " primary");
                    g.contests.add(c);
                }
            }
            Collections.sort(g.contests, new Comparator<Contest>() { public int compare(Contest a, Contest b) { return a.week != b.week ? a.week - b.week : Double.compare(b.delegates, a.delegates); } });
        }

        /** DNC formula: half Democratic vote over three elections, half electoral votes; plus 15% party leaders. */
        static double demDelegates(int s) {
            double dv = 0, tdv = 0;
            for (StateDef d : STATES) tdv += d.d16 + d.d20 + d.d24;
            StateDef d = STATES[s];
            dv = d.d16 + d.d20 + d.d24;
            double f = 0.5 * dv / tdv + 0.5 * d.ev / 538.0;
            return Math.round(3220 * f * 1.15);
        }

        static double repDelegates(int s) {
            StateDef d = STATES[s];
            int cds = Math.max(1, d.ev - 2);
            if (d.ab.equals("DC")) return 19;
            double v = 10 + 3 * cds + 3;
            if (d.r24 > d.d24) v += 4.5 + 0.6 * d.ev;
            return Math.round(v);
        }

        static double totalDelegates(Game g, int party) {
            double t = 0;
            for (Contest c : g.contests) if (c.party == party) t += c.delegates;
            return t;
        }

        // ---------------------------------------------------------------- primary debates
        static void primaryDebates(Game g) {
            String[] dem = {"2027-07-21", "2027-09-15", "2027-10-20", "2027-11-17", "2027-12-15", "2028-01-12", "2028-02-02"};
            String[] rep = {"2027-08-18", "2027-09-29", "2027-11-03", "2027-12-08", "2028-01-10"};
            int[] thrD = {1, 2, 3, 4, 6, 8, 10}, thrR = {1, 2, 3, 5, 8};
            int[] hostsD = {2, 0, 3, 1, 3, 0, 2}, hostsR = {4, 5, 4, 2, 4};
            for (int i = 0; i < dem.length; i++) {
                Debate d = new Debate();
                d.party = DEM; d.week = weekOfStr(dem[i]); d.host = hostsD[i]; d.thresholdPoll = thrD[i]; d.thresholdDonors = 40000 + i * 30000;
                g.debates.add(d);
            }
            for (int i = 0; i < rep.length; i++) {
                Debate d = new Debate();
                d.party = REP; d.week = weekOfStr(rep[i]); d.host = hostsR[i]; d.thresholdPoll = thrR[i]; d.thresholdDonors = 40000 + i * 30000;
                g.debates.add(d);
            }
        }

        // ---------------------------------------------------------------- Senate
        static void senate(Game g) {
            for (String[] r : SENATE_2028) {
                SenateRace sr = new SenateRace();
                sr.state = stateIdx(r[0]);
                sr.incumbent = r[1];
                sr.party = r[2].equals("D") ? DEM : REP;
                sr.base = Double.parseDouble(r[3]);
                sr.note = r[4];
                sr.open = r[4].contains("would be") || r[4].contains("governor") || r[4].contains("pledged");
                g.senate.add(sr);
            }
        }
    }

    // =====================================================================
    //  11.  THE WEEK  -  resolving every campaign's schedule, money, ads and field program
    // =====================================================================
    static final class Week {
        static final double ORGANIZER_WEEK = 1350, CALL_HUBS_BONUS = 1.0;
        /** Fraction of a week being resolved: 1 for weekly turns, 1/7 for the daily turns of the final stretch. */
        static double F = 1;
        static final String DONOR_HUBS = "NY CA TX FL IL DC MA CT WA GA VA MD NJ";

        static boolean contestant(Game g, Camp k) {
            if (!k.active) return false;
            if (k.party == IND) return true;
            if (g.phase >= PH_CONVENTION) return k.nominee;
            return k.party == DEM || k.party == REP;
        }

        /** Weeks until the vote that matters most to this campaign in this state. */
        static int weeksToVote(Game g, Camp k, int s) {
            if (g.phase >= PH_CONVENTION || k.nominee) return Math.max(0, ELECTION_WEEK - g.week);
            for (Contest c : g.contests) if (c.party == k.party && c.state == s) return Math.max(0, c.week - g.week);
            return 99;
        }

        static double stateCostIndex(int s) { return 0.75 + STATES[s].income / 120000.0; }

        static double officeWeek(int s) { return 850 * stateCostIndex(s) + 350; }

        /** HQ payroll per week by phase and tier. */
        static double hqWeek(Game g, Camp k) {
            double[] tier = {0.5, 1.0, 1.9};
            double base = g.phase == PH_INVISIBLE ? (k.announced ? 0.55e6 : 0.15e6) : g.phase == PH_PRIMARY ? 1.4e6 : 4.2e6;
            if (k.party >= GRN && k.party != IND) base *= 0.15;
            if (k.party == IND) base = g.phase >= PH_GENERAL || g.set.mode == MODE_GENERAL ? 3.0e6 : 0.7e6;
            return base * tier[k.hq];
        }

        /** Cost of one gross rating point in a market this week for this campaign. */
        static double cpp(Game g, int m, boolean candidate) {
            double hh = Elect.marketHH[m];
            double rate = hh > 1500 ? 0.55 : hh > 600 ? 0.62 : 0.72;
            double c = hh * rate;
            int w = g.week;
            double season = w < 60 ? 0.85 : w < 94 ? 1.0 : w < 98 ? 1.25 : w < 102 ? 1.6 : 2.0;
            double hot = 0;
            for (int s : Elect.marketStates[m]) if (Math.abs(Elect.lean[s] + 1.5) < 8) hot = Math.max(hot, Elect.dmaShare[m][s]);
            if (w >= 94) c *= 1 + 0.35 * hot;
            c *= season;
            if (candidate && (w >= 96 || g.phase == PH_PRIMARY)) c *= 0.75;     // lowest unit rate window
            return c;
        }

        /** Resolves the whole week. Called after the player has set orders and any debate this week is done. */
        static void resolve(Game g) {
            F = g.day >= 0 ? 1.0 / 7 : 1;
            boolean plan = g.day <= 0;
            if (plan) for (Camp k : g.camps) if (!k.human && (contestant(g, k) || k.party == IND && k.active)) AI.decide(g, k);
            Camp me = g.me();
            if (plan && me != null && me.active) Staff.automate(g, me);
            for (Camp k : g.camps) if (contestant(g, k) || (k.party >= GRN && k.active && g.phase >= PH_CONVENTION) || (k.party == IND && k.active)) campaign(g, k);
            Message.resolve(g);
            World.week(g);
            Events.week(g);
            Ballot.week(g);
            if (g.phase <= PH_PRIMARY && g.set.mode == MODE_FULL) Primary.week(g);
            if (g.phase >= PH_CONVENTION) General.week(g);
            Polls.weekly(g);
            if (g.day < 0) { history(g); g.week++; }
            else if (++g.day >= 7) { history(g); g.week++; g.day = 0; }
            F = 1;
            Primary.phaseCheck(g);
            if (g.day < 0 && g.week >= DAILY_START && g.phase >= PH_CONVENTION && g.phase != PH_OVER) startStretch(g);
        }

        /** The final two weeks: one day per turn and a closing argument. */
        static void startStretch(Game g) {
            g.day = 0;
            g.log(NK_BIG, "Two weeks to go. From now on every day counts: each turn is one day until Election Day.");
            for (Camp k : g.camps) {
                if (!k.active || !(k.nominee || k.party == IND)) continue;
                if (k.human) g.pending.add(new Object[]{EV_CLOSING, "Your closing argument", "", new String[0], -1});
                else Closing.choose(g, k, Closing.aiPick(g, k));
            }
        }

        static void history(Game g) {
            int w = Math.min(109, g.week);
            for (Camp k : g.camps) {
                double v = 0, d = 0;
                for (int s = 0; s < NS; s++) { v += k.volunteers[s]; d += k.doorsW[s]; }
                k.volHist[w] = v; k.doorsHist[w] = d;
            }
            g.natHist[w] = g.natPubAvg;
            if (g.natIHist == null) g.natIHist = new double[110];
            g.natIHist[w] = g.natPubI;
        }

        // ---------------------------------------------------------------- one campaign's week
        static void campaign(Game g, Camp k) {
            Arrays.fill(k.spendWeek, 0);
            double raisedBefore = k.raised, spentBefore = k.spent;
            double fatigueMul = 1.3 - k.a(A_STA) / 150.0;
            // fixed costs
            pay(g, k, hqWeek(g, k) * F, -1);
            for (int s = 0; s < NS; s++) {
                if (k.offices[s] > 0) pay(g, k, k.offices[s] * officeWeek(s) * F, s);
                if (k.organizers[s] > 0) pay(g, k, k.organizers[s] * ORGANIZER_WEEK * stateCostIndex(s) * F, s);
            }
            // itinerary (in the final stretch, one day at a time)
            int prevLoc = k.location;
            int s0 = g.day >= 0 ? g.day * 3 : 0, s1 = g.day >= 0 ? s0 + 3 : NSLOT;
            for (int i = s0; i < s1; i++) {
                int a = k.act[i];
                if (a == ACT_NONE) continue;
                int s = ACT_LOCAL[a] ? (k.loc[i] >= 0 ? k.loc[i] : k.location) : k.location;
                double eff = 1;
                if (ACT_LOCAL[a] && s != prevLoc) {
                    double dist = Math.hypot(STATES[s].cx - STATES[prevLoc].cx, STATES[s].cy - STATES[prevLoc].cy);
                    boolean overnight = i % 3 == 0;
                    if (dist > 220 && !overnight) { eff = 0.7; k.stamina -= 3; }
                    prevLoc = s;
                }
                if (k.stamina < 35) eff *= 0.85;
                activity(g, k, a, s, k.param[i], eff);
                k.stamina -= ACT_FATIGUE[a] * fatigueMul * 0.55;
            }
            k.location = prevLoc;
            k.stamina = clamp(k.stamina + (38 + 0.12 * k.a(A_STA)) * F, 5, 100);
            Issues.week(g, k);
            surrogates(g, k);
            fundraising(g, k);
            advertising(g, k);
            field(g, k);
            superPacs(g, k);
            NameId.week(g, k);
            decay(g, k);
            for (int s = 0; s < NS; s++) k.spend4[s] = k.spend4[s] * 0.75 + k.spendWeek[s];
            int w = Math.min(109, g.week);
            k.raisedHist[w] = k.raised - raisedBefore;
            k.spentHist[w] = k.spent - spentBefore;
            if (k.cash < 0) {
                k.debt -= k.cash;
                k.cash = 0;
                if (k.human) g.log(NK_BAD, "The campaign is out of cash and running up debt. Vendors and staff go unpaid; morale suffers.");
                k.enthusiasm -= 0.01;
            }
        }

        static void pay(Game g, Camp k, double amt, int s) {
            if (k.human) amt *= g.set.difficulty == 0 ? 0.9 : g.set.difficulty == 2 ? 1.06 : 1.0;
            k.cash -= amt;
            k.spent += amt;
            if (s >= 0) { k.spendState[s] += amt; k.spendWeek[s] += amt; }
        }

        static void raise(Camp k, double amt, int src) { k.cash += amt; k.raised += amt; k.raisedBy[src] += amt; }

        /** Events cost less in the invisible primary (smaller venues, less advance work) than in the general. */
        static double eventCost(Game g, Camp k, int a) {
            double m = g.phase == PH_INVISIBLE ? 0.35 : g.phase == PH_PRIMARY && !k.nominee ? 0.6 : 1.0;
            return ACT_COST[a] * m;
        }

        static boolean primaryEarly(Game g, Camp k, int s) {
            if (g.phase > PH_PRIMARY || k.nominee) return false;
            for (Contest c : g.contests) if (c.party == k.party && c.state == s) return c.week <= weekOfEarlyEnd(g, k.party) && c.week >= g.week;
            return false;
        }

        static int weekOfEarlyEnd(Game g, int party) { return party == DEM ? Setup.weekOfStr("2028-02-29") : Setup.weekOfStr("2028-02-29"); }

        /** Effects of one scheduled event. */
        static void activity(Game g, Camp k, int a, int s, int param, double eff) {
            boolean primary = g.phase <= PH_PRIMARY && !k.nominee;
            boolean early = primaryEarly(g, k, s);
            double cha = k.a(A_CHA) / 100, spe = k.a(A_SPE) / 100, ret = k.a(A_RET) / 100, intl = k.a(A_INT) / 100, aut = k.a(A_AUT) / 100;
            double vol = k.a(A_VOL) / 100, vir = k.a(A_VIR) / 100, big = k.a(A_BIG) / 100, pod = k.a(A_POD) / 100;
            double risk = 1;
            pay(g, k, eventCost(g, k, a), ACT_LOCAL[a] ? s : -1);
            switch (a) {
                case ACT_RALLY: {
                    double crowd = Rallies.hold(g, k, s, eff);
                    k.visit[s] += 1.1 * crowd * (early ? 1.6 : 1);
                    k.volunteers[s] += (180 + 520 * vol) * crowd * (g.phase >= PH_CONVENTION ? 1.6 : 1);
                    k.enthusiasm += 0.0012 * crowd * (1 - k.enthusiasm);
                    NameId.learnState(k, s, 0.02 * crowd);
                    k.smallList += 900 * crowd * (0.5 + vir);
                    k.localImage[s] += 0.004 * crowd;
                    Issues.speak(k, 0.6);
                    risk = 1.0;
                    break;
                }
                case ACT_TOWNHALL:
                    Issues.speak(k, 0.5);
                    k.visit[s] += 0.8 * eff * (early ? 1.7 : 1);
                    k.localImage[s] += 0.006 * (ret + intl) / 1.5 * eff;
                    k.favor += 0.001 * eff;
                    risk = 1.6;
                    break;
                case ACT_RETAIL:
                    k.visit[s] += (early ? 1.9 : 0.6) * (0.5 + ret) * eff;
                    k.localImage[s] += 0.004 * (0.4 + ret) * (early ? 2 : 1) * eff;
                    risk = 0.8;
                    break;
                case ACT_FUNDRAISER: {
                    double hub = DONOR_HUBS.contains(STATES[s].ab) ? 1.0 : 0.45;
                    double amt = (0.35e6 + 2.2e6 * big) * hub * moneyMomentum(g, k) * saturation(g, k) * eff;
                    raise(k, amt, RS_EVENTS);
                    k.donorsTapped += amt;
                    k.localImage[s] -= 0.001;
                    risk = 0.6;
                    break;
                }
                case ACT_CALLTIME: {
                    double amt = (0.12e6 + 0.85e6 * big) * moneyMomentum(g, k) * saturation(g, k);
                    raise(k, amt, RS_CALL);
                    k.donorsTapped += amt;
                    risk = 0.1;
                    break;
                }
                case ACT_PODCAST: {
                    Appearance ap = Podcasts.appear(g, k, param, eff);
                    Issues.speak(k, 0.5);
                    risk = ap.declined ? 0 : 1.3;
                    break;
                }
                case ACT_TV:
                    k.favor += 0.0025 * (intl + k.a(A_DIS) / 100 - 1.1);
                    NameId.boost(k, 0.0025);
                    Issues.speak(k, 0.6);
                    risk = 0.9;
                    break;
                case ACT_HOSTILE: {
                    double q = (k.a(A_DEB) / 100 + k.a(A_XOV) / 100 - 1.0) * 0.015 + 0.002;
                    int[] gs = k.party == DEM ? new int[]{8, 21, 25, 2, 13} : new int[]{9, 21, 4, 12, 16};
                    for (int gi : gs) k.imageG[gi] += q;
                    k.cred[I_DEMOC] += 0.002;
                    risk = 1.8;
                    break;
                }
                case ACT_SPEECH:
                    Issues.build(k, k.focusIssue, 0.03 * (spe + intl) / 1.6 * eff);
                    if (k.nominee || k.human) g.newsSal[k.focusIssue] += 0.015;
                    k.visit[s] += 0.6 * eff;
                    risk = 0.7;
                    break;
                case ACT_UNION:
                    k.imageG[24] += 0.006 * (0.5 + aut) * eff;
                    k.imageG[8] += 0.001;
                    k.visit[s] += 0.6 * eff;
                    k.volunteers[s] += 150 * eff;
                    break;
                case ACT_CHURCH: {
                    int gi = clampi(param, 0, CHURCH_GROUPS.length - 1);
                    k.imageG[CHURCH_GROUPS[gi]] += 0.008 * (0.4 + aut) * eff;
                    k.visit[s] += 0.5 * eff;
                    break;
                }
                case ACT_CAMPUS:
                    k.imageG[0] += 0.004 * (0.5 + cha) * eff;
                    k.imageG[28] += 0.007 * eff;
                    k.volunteers[s] += (120 + 300 * vol) * eff;
                    k.visit[s] += 0.4 * eff;
                    risk = 1.2;
                    break;
                case ACT_COMMUNITY: {
                    int gi = COMMUNITY_GROUPS[clampi(param, 0, COMMUNITY_GROUPS.length - 1)];
                    k.imageG[gi] += 0.008 * (0.4 + ret) * eff;
                    k.visit[s] += 0.5 * eff;
                    k.volunteers[s] += 80 * eff;
                    break;
                }
                case ACT_PREP:
                    k.prep += 9 * (1 - k.prep / 130) * (0.6 + intl * 0.5);
                    risk = 0;
                    break;
                case ACT_REST:
                    k.stamina += 6;
                    risk = 0;
                    break;
                case ACT_ENDORSE:
                    Primary.seekEndorsement(g, k);
                    risk = 0.2;
                    break;
                case ACT_DINNER:
                    k.localImage[s] += 0.008 * (0.4 + spe) * (early ? 2 : 0.7) * eff;
                    k.visit[s] += 0.5 * eff;
                    k.volunteers[s] += 60;
                    break;
                default: break;
            }
            // gaffes
            if (risk > 0) {
                double p = 0.0035 * risk * (1 + (100 - k.a(A_DIS)) / 35.0) * (k.stamina < 40 ? 1.8 : 1.0) * g.set.eventRate * (k.nominee || k.human ? 1.0 : 0.6);
                if (g.rng.nextDouble() < p) Events.gaffe(g, k, a, s);
            }
        }

        static final int[][] PODCAST_GROUPS = {{2, 5, 13}, {3, 0, 9}, {11, 2, 5}, {6, 29, 7}, {9, 16, 21}};
        static final String[] PODCAST_KIND = {"Comedy / manosphere", "Progressive & news", "Sports", "Faith & family", "Business & tech"};
        static final int[] CHURCH_GROUPS = {10, 6, 7, 17, 18};
        static final String[] CHURCH_KIND = {"Black church", "Evangelical", "Catholic parish", "Synagogue", "Mosque"};
        static final int[] COMMUNITY_GROUPS = {10, 12, 16, 20, 18, 27, 25};
        static final String[] COMMUNITY_KIND = {"Black community", "Hispanic community", "Asian American community", "Tribal nation", "Arab American community", "LGBTQ community", "Veterans"};

        static void viral(Game g, Camp k, String what) {
            k.smallList += 80000 * (0.5 + k.a(A_VIR) / 100.0);
            NameId.boost(k, 0.012);
            k.favor += 0.006;
            if (k.human || (k.nominee && k.party <= REP)) g.log(k.human ? NK_GOOD : NK_INFO, "Viral: " + what + " featuring " + k.name() + " racks up millions of views.");
        }

        /** Donor fatigue: beyond ~8 million names each new name is worth less. */
        static double effectiveList(double list) {
            double b = 8e6;
            return list <= b ? list : b + b * Math.log(1 + (list - b) / b);
        }

        static double moneyMomentum(Game g, Camp k) {
            double m = 1 + 0.6 * k.momentum;
            if (g.phase >= PH_CONVENTION && k.nominee) return clamp(m, 0.6, 1.4);
            if (g.phase <= PH_PRIMARY && !k.nominee) {
                double[] nat = g.primaryPoll;
                if (nat != null && k.idx < nat.length) m *= 0.55 + 2.2 * Math.min(0.5, nat[k.idx]);
            }
            return clamp(m, 0.3, 1.8);
        }

        /** Big donors get tapped out; a new cycle of limits opens for the general. */
        static double saturation(Game g, Camp k) {
            double cap = (g.phase >= PH_CONVENTION ? 260e6 : 70e6) * (0.4 + k.a(A_BIG) / 100.0);
            return clamp(1 - k.donorsTapped / cap, 0.12, 1);
        }

        static void fundraising(Game g, Camp k) {
            // dollars per list member per week: ~$0.25 in the invisible primary, ~$0.75 in the primaries, ~$1.25 in the general
            double phaseMul = g.phase == PH_INVISIBLE ? (k.announced ? 1.0 : 0.15) : g.phase == PH_PRIMARY ? (k.nominee ? 4.0 : 3.0) : 5.0;
            int left = ELECTION_WEEK - g.week;
            if (g.phase >= PH_CONVENTION && left < 7) phaseMul *= 1.35;
            double small = effectiveList(k.smallList) * 0.22 * (0.4 + 0.6 * k.a(A_SMD) / 100) * (0.6 + 0.8 * k.enthusiasm) * phaseMul * moneyMomentum(g, k) * F;
            if (k.party >= GRN && k.party != IND) small *= 0.5;
            if (k.party == IND) small *= 0.45 + 2.2 * Math.min(0.3, g.thirdPoll(k));
            if (!k.human) small *= g.set.difficulty == 0 ? 0.9 : g.set.difficulty == 2 ? 1.12 : 1.0;
            raise(k, small, RS_SMALL);
            // list growth from attention, minus unsubscribes; digital ads acquire new names at about $8 each
            k.smallList *= Math.pow(1 + 0.0012 * k.a(A_VIR) / 50.0 - 0.004, F);
            if (k.selfFundW > 0) {
                double left$ = k.c.selfMax - k.selfFunded;
                double amt = Math.min(k.selfFundW * F, Math.max(0, left$));
                if (amt > 0) {
                    k.selfFunded += amt;
                    raise(k, amt, RS_SELF);
                }
                if (k.c.selfMax - k.selfFunded < 1) {
                    k.selfFundW = 0;
                    if (k.human) g.log(NK_BAD, "Self-funding: you have put in everything you can (" + money(k.selfFunded) + ").");
                }
            }
            // endorsing groups bring money
            for (int e : k.endorsements) raise(k, ENDORSERS[e].money / 40.0 * F, RS_GROUPS);
        }

        static void surrogates(Game g, Camp k) {
            if (g.phase < PH_CONVENTION || !k.nominee) return;
            for (int i = 0; i < SURROGATES.length; i++) {
                int s = k.surrogateState[i];
                if (s < 0) continue;
                if (Integer.parseInt(SURROGATES[i][1]) != k.party) continue;
                double pow = Integer.parseInt(SURROGATES[i][2]) / 100.0;
                k.visit[s] += 0.6 * pow;
                k.volunteers[s] += 250 * pow;
                if (SURROGATES[i][0].equals("Donald Trump")) {
                    k.enthusiasm += 0.002;
                    k.localImage[s] -= 0.002;
                    k.imageG[21] -= 0.002;
                    k.imageG[8] += 0.003;
                }
                if (SURROGATES[i][0].equals("Barack Obama")) { k.imageG[10] += 0.004; raise(k, 1.5e6, RS_EVENTS); }
                if (SURROGATES[i][0].equals("Bernie Sanders")) k.imageG[0] += 0.004;
                if (SURROGATES[i][0].equals("Elon Musk")) { k.imageG[2] += 0.003; k.imageG[4] -= 0.003; }
            }
            if (k.vpSurrogateState >= 0) {
                Cand vp = g.vpOf(k.party);
                if (vp != null) {
                    int s = k.vpSurrogateState;
                    k.visit[s] += 0.7 * (0.4 + vp.a[A_CHA] / 100.0);
                    k.volunteers[s] += 200;
                }
            }
        }

        /** TV and digital orders: pay this week and add to the persuasion stock in each state. */
        static void advertising(Game g, Camp k) {
            double quality = (0.75 + 0.25 * k.a(A_DIS) / 100.0) * (k.hq == 0 ? 0.88 : k.hq == 2 ? 1.08 : 1.0);
            double[] addTv = new double[NS];
            for (int m = 0; m < Elect.NM; m++) {
                double grp = k.tvGrp[m] * F;
                if (grp <= 0) continue;
                double cost = grp * cpp(g, m, true);
                if (k.cash < cost && k.cash < 0.5e6) { if (k.human) g.log(NK_BAD, "Ads in " + Elect.marketName[m] + " pulled: not enough cash."); continue; }
                int tone = k.tvTone[m];
                double toneMul = tone == 0 ? 0.9 : tone == 1 ? 1.1 : 1.25;
                for (int s : Elect.marketStates[m]) {
                    double sh = Elect.dmaShare[m][s];
                    addTv[s] += grp * sh * toneMul * quality;
                    k.toneStock[s] += grp * sh * tone * 0.0005;
                    pay(g, k, cost * sh / Math.max(0.01, sumShare(m)), s);
                }
            }
            for (int n = 0; n < NATIONAL_TV.length; n++) {
                double grp = k.nationalGrp[n] * F;
                if (grp <= 0) continue;
                double cost = grp * NATIONAL_CPP[n] * (g.week >= 98 ? 1.5 : 1);
                pay(g, k, cost, -1);
                for (int s = 0; s < NS; s++) {
                    double f = n == 2 ? STATES[s].hisp / 30.0 : 1;
                    addTv[s] += grp * f * quality * (n == 1 ? 0.6 : 0.9);
                }
            }
            double[] digTot = new double[NDM];
            for (int s = 0; s < NS; s++) {
                k.tvStock[s] += addTv[s];
                double d = k.digital[s] * F;
                if (d > 0) {
                    pay(g, k, d, s);
                    Digital.spend(g, k, s, d, quality, digTot);
                    k.volunteers[s] += d / 2500.0 * (0.3 + k.a(A_VOL) / 200.0);
                }
                double tr = k.transferW[s] * F;
                if (tr > 0) {
                    pay(g, k, tr, s);
                    k.transfers[s] += tr;
                    k.volunteers[s] += tr / 4000.0;
                }
                k.digStock[s] = (k.digA[s][0] * 0.2 + k.digA[s][1] * 0.57 + k.digA[s][2] * 0.23);
            }
            Digital.effects(g, k, digTot);
        }

        static double sumShare(int m) { double t = 0; for (int s : Elect.marketStates[m]) t += Elect.dmaShare[m][s]; return t; }

        static double[] cvapSum;

        static double sumCvap(int s) {
            if (cvapSum == null) {
                cvapSum = new double[NS];
                for (int ss = 0; ss < NS; ss++) for (int c = 0; c < NCELL; c++) cvapSum[ss] += Elect.cvap[ss][c];
            }
            return cvapSum[s];
        }

        /** Saturating effect of the persuasion stock (share of adults contacted recently). */
        static double fieldSat(double x) { return 0.1 * Math.tanh(x * 10); }

        /** Saturating turnout lift from the GOTV stock. */
        static double gotvSat(double x) { return 0.12 * Math.tanh(x / 0.12); }

        /**
         * Organisers recruit volunteers; volunteers take shifts; shifts knock doors and make calls.
         * Scale: a top battleground (Pennsylvania) with ~350 organisers produces ~15-20K active volunteers,
         * ~300K doors a week in October and ~1M in the final week; ~40-50M doors nationally over a cycle.
         */
        static void field(Game g, Camp k) {
            for (int s = 0; s < NS; s++) {
                double idx = stateCostIndex(s);
                // orders: open or close offices, hire or lay off organisers (one-time costs)
                if (k.officeOrder[s] != 0) {
                    if (k.officeOrder[s] > 0) pay(g, k, k.officeOrder[s] * (12000 * idx), s);
                    k.offices[s] = Math.max(0, k.offices[s] + k.officeOrder[s]);
                    k.officeOrder[s] = 0;
                }
                if (k.organizerOrder[s] != 0) {
                    if (k.organizerOrder[s] > 0) pay(g, k, k.organizerOrder[s] * 2500, s);
                    k.organizers[s] = Math.max(0, k.organizers[s] + k.organizerOrder[s]);
                    k.organizerOrder[s] = 0;
                }
                // the state party's coordinated campaign, funded by transfers, staffs up with a lag
                k.partyOrg[s] += (k.transferW[s] / (ORGANIZER_WEEK * idx) * 0.85 - k.partyOrg[s]) * 0.25;
                double org = k.organizers[s] + k.partyOrg[s];
                double cap = k.offices[s] * 8 + 2;
                double effOrg = Math.min(org, cap) + Math.max(0, org - cap) * 0.4;
                int left = weeksToVote(g, k, s);
                double urgency = 0.6 + 1.2 * Math.exp(-left / 8.0);
                double recruits = effOrg * (3 + 5 * k.a(A_VOL) / 100.0) * (0.5 + k.enthusiasm) * urgency * F;
                recruits += k.volunteers[s] * 0.015 * (0.5 + k.enthusiasm) * F;       // relational growth
                double churn = k.volunteers[s] * (effOrg < 1 ? 0.12 : 0.06) * F;
                k.volunteers[s] = Math.max(0, k.volunteers[s] + recruits - churn);
                // each organiser keeps about 45 volunteers active; a few self-organise
                double active = Math.min(k.volunteers[s] * 0.35, effOrg * 45 + k.volunteers[s] * 0.04);
                double perVol = 0.7 + 1.6 * Math.exp(-left / 2.5);
                if (left > 30) perVol *= 0.6;
                double shifts = active * perVol * F * (g.day >= 5 ? 1.35 : g.day >= 0 ? 0.88 : 1);
                StateDef d = STATES[s];
                double density = 22 + 0.33 * d.urban + 0.12 * (100 - d.rural - d.urban);
                double doors = shifts * 0.55 * density;
                double calls = shifts * 0.22 * 150;
                double texts = shifts * 0.13 * 1200;
                double rel = shifts * 0.10 * 12;
                boolean gotvWindow = left <= 5;
                double contacts = doors * (gotvWindow ? 0.30 : 0.22) + calls * 0.09 * 0.6 + texts * 0.04 * 0.3 + rel * 0.35 * 1.6;
                double cv = Math.max(1, sumCvap(s));
                k.shiftsW[s] = shifts; k.doorsW[s] = doors; k.callsW[s] = calls; k.textsW[s] = texts; k.relW[s] = rel;
                k.shiftsT[s] += shifts; k.doorsT[s] += doors; k.callsT[s] += calls; k.textsT[s] += texts; k.relT[s] += rel;
                k.persuade[s] += contacts * 0.30 / cv;
                if (left <= 6) k.gotv[s] += contacts * 0.70 / cv;
            }
        }

        static void superPacs(Game g, Camp k) {
            if (k.party >= GRN && k.party != IND) return;
            boolean general = g.phase >= PH_CONVENTION && k.nominee || k.party == IND;
            double budget = general ? (2.5e6 + 9e6 * k.a(A_BIG) / 100 + 4e6 * k.a(A_EST) / 100) * (g.week >= 98 ? 2.2 : 1.0)
                    : (k.announced ? (0.15e6 + 1.0e6 * k.a(A_BIG) / 100) : 0);
            budget *= F;
            if (budget <= 0) return;
            double[] val = AI.stateValue(g, k);
            double tot = 0;
            for (int s = 0; s < NS; s++) { if (k.redBox[s]) val[s] *= 2.5; tot += val[s]; }
            if (tot <= 0) return;
            for (int s = 0; s < NS; s++) {
                double amt = budget * val[s] / tot;
                if (amt < 1000) continue;
                double grp = amt / (Math.max(150, STATE_TVHH[s] * 0.62) * (g.week >= 98 ? 1.8 : 1.2));
                k.superPac[s] += grp;
            }
        }

        static double dk(double rate) { return F >= 1 ? rate : Math.pow(rate, F); }

        static void decay(Game g, Camp k) {
            double tv = dk(0.48), pac = dk(0.5), dg = dk(0.5), tone = dk(0.7), vis = dk(0.55), per = dk(0.92), gotv = dk(0.85), li = dk(0.93);
            for (int s = 0; s < NS; s++) {
                k.tvStock[s] *= tv;
                k.superPac[s] *= pac;
                k.digStock[s] *= dg;
                for (int a = 0; a < NAGE; a++) k.digA[s][a] *= dg;
                k.toneStock[s] *= tone;
                k.visit[s] *= vis;
                k.persuade[s] *= per;
                k.gotv[s] *= gotv;
                k.localImage[s] *= li;
            }
            double ig = dk(0.95);
            for (int i = 0; i < NG; i++) k.imageG[i] *= ig;
            double fo = dk(0.8);
            for (int i = 0; i < NI; i++) k.focus[i] *= fo;
            k.favor *= dk(0.95);
            k.momentum *= dk(0.88);
            k.enthusiasm = clamp(k.enthusiasm + (Vote.baseEnthusiasm(k) - k.enthusiasm) * 0.05 * F, 0.15, 0.95);
            double at = dk(0.94);
            for (int a = 0; a < ATTACK.length; a++) k.attackStick[a] *= at;
            k.negativity *= dk(0.85);
            if (g.phase >= PH_CONVENTION) k.prep *= dk(0.93);
        }
    }

    // =====================================================================
    //  12.  THE WORLD  -  economy, approval, the news cycle, message wars and polling
    // =====================================================================
    static final class World {
        /** Fundamentals: presidential approval, the economy and the midterm signal move the national environment. */
        static void environment(Game g) {
            double fund = -0.30 * (g.approval - 45) - 0.5 * (g.gdp - 2) + 0.7 * (g.unemp - 4.5) + 0.4 * (g.inflation - 2.5);
            fund += 0.06 * (g.set.midGeneric - 2);
            if (g.set.scenario == SC_WAR && g.warStart >= 0 && g.week >= g.warStart) {
                double w = g.week - g.warStart;
                fund += w < 6 ? -2.5 * (1 - w / 6.0) : Math.min(3.0, (w - 6) * 0.12);
            }
            g.env = fund + g.envDrift;
        }

        static void week(Game g) {
            Random r = g.rng;
            double[] st = SCEN_START[g.set.scenario];
            double targetApp = st[1];
            if (g.set.scenario == SC_BASE) targetApp = 40.5 + Math.min(3.0, Math.max(0, g.week - 40) * 0.05);
            if (g.set.scenario == SC_SCANDAL) targetApp = 31 + Math.min(4, g.week * 0.03);
            if (g.set.scenario == SC_WAR && g.warStart >= 0 && g.week >= g.warStart) {
                double w = g.week - g.warStart;
                targetApp = w < 6 ? 50 - w : Math.max(33, 44 - (w - 6) * 0.25);
            }
            if (g.set.scenario == SC_AIREC) targetApp = Math.max(33, 37 - g.week * 0.02);
            if (g.set.scenario == SC_BOOM) targetApp = Math.min(51, 48 + g.week * 0.02);
            double F = Week.F, sf = Math.sqrt(F);
            g.approval += (targetApp - g.approval) * 0.08 * F + r.nextGaussian() * 0.55 * sf;
            g.approval = clamp(g.approval, 22, 62);
            g.envDrift = g.envDrift * Math.pow(0.96, F) + r.nextGaussian() * 0.22 * sf;
            if (g.week % 4 == 0 && g.day <= 0) economy(g);
            double nd = Math.pow(0.85, F);
            for (int i = 0; i < NI; i++) g.newsSal[i] *= nd;
            double[] base = new double[NI];
            Arrays.fill(base, 1);
            Game tmp = g;
            switch (g.set.scenario) {
                case SC_SCANDAL: base[I_DEMOC] = 2.4; break;
                case SC_WAR: if (g.warStart >= 0 && g.week >= g.warStart) { base[I_CHINA] = 3.6; base[I_UKRAINE] = 1.2; base[I_COL] = 1.2; } else base[I_CHINA] = 1.6; break;
                case SC_AIREC: base[I_AI] = 2.6; base[I_JOBS] = 2.6; base[I_COL] = 1.1; break;
                case SC_BOOM: base[I_COL] = 0.7; base[I_JOBS] = 0.7; base[I_IMMIG] = 1.2; break;
                case SC_THIRD: base[I_DEMOC] = 1.4; break;
                default: break;
            }
            base[I_COL] *= 1 + Math.max(0, tmp.inflation - 2.5) * 0.15;
            base[I_JOBS] *= 1 + Math.max(0, tmp.unemp - 4.5) * 0.25;
            for (int i = 0; i < NI; i++) g.salience[i] = base[i] * (1 + g.newsSal[i]);
            g.mood = 1.0 + (g.set.scenario == SC_SCANDAL ? 0.02 : 0) + (g.set.scenario == SC_WAR ? 0.02 : 0) + (g.set.scenario == SC_THIRD ? 0.015 : 0);
            environment(g);
        }

        static void economy(Game g) {
            Random r = g.rng;
            double tu, ti, tg;
            switch (g.set.scenario) {
                case SC_AIREC: tu = Math.min(8.4, 5.2 + g.week * 0.03); ti = 2.4; tg = g.week < 50 ? -1.5 : 0.5; break;
                case SC_BOOM: tu = 3.7; ti = 2.0; tg = 3.4; break;
                case SC_WAR: boolean war = g.warStart >= 0 && g.week >= g.warStart; tu = war ? 5.8 : 4.6; ti = war ? 4.6 : 2.9; tg = war ? -0.5 : 1.6; break;
                case SC_SCANDAL: tu = 4.8; ti = 3.0; tg = 1.2; break;
                default: tu = 4.6 + 0.1 * Math.sin(g.week / 13.0); ti = 2.8; tg = 1.7; break;
            }
            double ou = g.unemp;
            g.unemp = clamp(g.unemp + (tu - g.unemp) * 0.18 + r.nextGaussian() * 0.08, 2.8, 11);
            g.inflation = clamp(g.inflation + (ti - g.inflation) * 0.15 + r.nextGaussian() * 0.12, 0, 9);
            g.gdp = clamp(g.gdp + (tg - g.gdp) * 0.2 + r.nextGaussian() * 0.35, -5, 6);
            if (g.week > 0) {
                String dir = g.unemp > ou + 0.05 ? "rises" : g.unemp < ou - 0.05 ? "falls" : "holds";
                g.log(NK_INFO, "Jobs report: unemployment " + dir + " to " + fmt1(g.unemp) + "%. Inflation " + fmt1(g.inflation) + "%, growth " + fmt1(g.gdp) + "% annualised.");
            }
        }
    }

    // ---- message wars: frames, attacks and narrative ------------------------------------------------------------
    static final class Message {
        /** Fit of a message frame for a campaign this week (0..1.5). */
        static double frameFit(Game g, Camp k, int f) {
            double v = 0.6;
            switch (f) {
                case 0: v += 0.3 + 0.08 * (g.inflation - 2.5) + (k.party == DEM ? 0.1 : 0); break;
                case 1: v += (k.a(A_EXE) + k.a(A_FPC) - 100) / 200.0 + (g.set.scenario == SC_WAR ? 0.4 : 0); break;
                case 2: v += (k.a(A_EST) < 40 ? 0.3 : 0) + (k.party == DEM ? 0.15 : -0.1) + (g.approval < 42 && k.party == DEM ? 0.15 : 0); break;
                case 3: v += k.party == DEM ? 0.35 : -0.3; break;
                case 4: v += (k.a(A_FPC) - 50) / 120.0 + (k.party == REP ? 0.2 : 0); break;
                case 5: v += (k.a(A_XOV) - 50) / 100.0; break;
                case 6: v += (g.set.scenario == SC_SCANDAL ? 0.5 : 0.1) + (k.party == DEM ? 0.15 : -0.2) - k.a(A_SCA) / 200.0; break;
                case 7: v += (k.plat[I_HOUSING] > 0.3 ? 0.2 : 0) + (k.plat[I_AI] > 0.2 ? 0.1 : 0) + (g.set.scenario == SC_AIREC ? -0.2 : 0.05); break;
                case 8: v += (k.c.faction == F_PROG || k.c.faction == F_NATCON || k.c.faction == F_MAGA || k.c.faction == F_DSOC ? 0.35 : 0.05); break;
                case 9: v += k.party == REP ? 0.25 : (k.plat[I_CRIME] > 0.1 ? 0.15 : -0.1); break;
                case 10: v += k.party == REP ? 0.3 : (k.c.faction == F_MOD ? 0.1 : -0.2); break;
                case 11: v += k.party == REP ? (k.c.faction == F_HAWK || k.c.faction == F_GOV ? 0.05 : 0.35) : -0.5; break;
                case 12: v += 0.3 + (k.plat[I_SOCSEC] < -0.3 ? 0.15 : -0.1); break;
                case 13: v += (k.a(A_AUT) + k.a(A_CHA) - 120) / 160.0; break;
                default: break;
            }
            return clamp(v, 0, 1.5);
        }

        /** How well an attack line can stick to a target (0..1.2). */
        static double attackFit(Game g, Camp target, int a) {
            switch (a) {
                case 1: { double c = Math.abs(target.c.cult) * 0.5 + Math.abs(target.c.econ) * 0.5; return clamp((c - 0.3) * 1.4, 0.05, 1.2); }
                case 2: return clamp((target.c.selfMax > 50e6 ? 0.4 : 0) + (100 - target.a(A_AUT)) / 120.0 + (target.a(A_EST) > 75 ? 0.15 : 0), 0.05, 1.2);
                case 3: return clamp(target.a(A_SCA) / 90.0 + target.scandalRevealed * 0.4 + (target.party == REP && g.approval < 40 ? 0.25 : 0), 0.05, 1.2);
                case 4: return clamp((120 - target.a(A_EXE) - target.a(A_FPC)) / 110.0, 0.05, 1.2);
                case 5: return clamp((target.party == DEM ? (target.c.faction == F_DSOC || target.c.faction == F_PROG ? 0.6 : 0.3) : (target.a(A_EST) > 70 && target.c.faction == F_MAGA ? 0.7 : 0.4)), 0.05, 1.2);
                case 6: return clamp(target.flipflop * 0.5 + 0.05, 0.05, 1.2);
                case 7: return target.party == DEM ? clamp(0.3 + (-target.plat[I_CRIME] - target.plat[I_IMMIG]) * 0.45, 0.05, 1.2) : 0.05;
                case 8: return target.party == REP ? clamp(0.25 + target.plat[I_SOCSEC] * 0.8, 0.05, 1.2) : 0.1;
                case 9: { int age = 2028 - target.c.born; return age >= 70 ? clamp((age - 66) / 10.0, 0.1, 1.2) : 0.02; }
                case 10: return clamp((100 - target.a(A_DIS)) / 90.0 + target.a(A_VIR) / 300.0, 0.05, 1.2);
                default: return 0;
            }
        }

        static void resolve(Game g) {
            double F = Week.F;
            for (Camp k : g.camps) {
                if (!Week.contestant(g, k) && !(k.party == IND && k.active)) continue;
                // frame: consistency builds narrative strength; switching resets part of it
                int f = clampi(k.frame, 0, FRAME.length - 1);
                if (k.lastFrame >= 0 && k.lastFrame != f) for (int i = 0; i < FRAME.length; i++) if (i != f) k.frameStr[i] *= 0.55;
                k.lastFrame = f;
                double fit = frameFit(g, k, f);
                double disc = 0.5 + k.a(A_DIS) / 200.0;
                k.frameStr[f] = Math.min(1.5, k.frameStr[f] + 0.12 * fit * disc * F);
                for (int i : FRAME_ISSUES[f]) {
                    k.focus[i] += 0.1 * k.frameStr[f] * F;
                    if (k.nominee) g.newsSal[i] += 0.008 * k.frameStr[f] * F;
                    Issues.build(k, i, 0.004 * k.frameStr[f] * fit * F);
                }
                k.favor += 0.002 * k.frameStr[f] * (fit - 0.5) * F;
                if (f == 13) k.favor += 0.002 * (k.a(A_AUT) - 50) / 50.0 * F;
                // attack
                int at = k.attack;
                Camp target = attackTarget(g, k);
                if (at > 0 && target != null) {
                    double af = attackFit(g, target, at);
                    double force = 0.10 * af * (0.5 + k.a(A_DIS) / 200.0) * (0.6 + k.frameStr[f] * 0.3);
                    target.attackStick[at] += force * F;
                    k.negativity += 0.08 * F;
                    if (k.negativity > 0.35) k.favor -= 0.003 * (k.negativity - 0.35) * (1.3 - k.a(A_AUT) / 150.0) * F;
                    if (at == 9) k.imageG[1] -= 0.004 * F;
                }
                Closing.day(g, k);
            }
        }

        static Camp attackTarget(Game g, Camp k) {
            if (k.attackTarget >= 0) {
                Camp t = g.camp(k.attackTarget);
                if (t != null && t.active && t.party == k.party || t != null && t.nominee && g.phase >= PH_CONVENTION || t != null && k.party == IND && t.active && t.party <= REP) return t;
            }
            if (k.party == IND) return Issues.rival(g, k);
            if (g.phase >= PH_CONVENTION && k.party > REP) return g.nominee(DEM);
            if (g.phase >= PH_CONVENTION) {
                Camp ind = g.indep();
                if (ind != null && ind.party == IND && Polls.trueShare(g, ind) > Polls.trueShare(g, g.nominee(k.party == DEM ? REP : DEM))) return ind;
                return g.nominee(k.party == DEM ? REP : DEM);
            }
            Camp best = null;
            double[] nat = g.primaryPoll;
            for (Camp o : g.camps) if (o != k && o.active && o.party == k.party && (best == null || nat != null && nat[o.idx] > nat[best.idx])) best = o;
            return best;
        }
    }

    // ---- polling --------------------------------------------------------------------------------------------------------
    static final class Polls {
        static final String[] POLLSTERS = {"Emerson", "Quinnipiac", "Marist", "Siena/NYT", "Fox News", "CNN/SSRS", "YouGov", "AtlasIntel",
                "Trafalgar", "InsiderAdvantage", "Rasmussen", "Morning Consult"};
        static double[][] cache;
        static int cacheWeek = -1;

        /** The 'true' state of the race this week (cached). */
        static double[][] truth(Game g) {
            if (cache == null || cacheWeek != g.week || g.dirty) {
                double[] tv = new double[g.camps.size()];
                cache = Vote.general(g, cache, tv);
                double tot = 0;
                for (double[] row : cache) tot += row[3];
                for (int i = 0; i < tv.length; i++) tv[i] /= Math.max(1, tot);
                g.thirdShare = tv;
                cacheWeek = g.week;
                g.dirty = false;
            }
            return cache;
        }

        /** A campaign's share of the national vote in the current model (general election). */
        static double trueShare(Game g, Camp k) {
            if (k == null) return 0;
            double[][] t = truth(g);
            double d = 0, r = 0, o = 0, ind = 0;
            for (double[] row : t) { d += row[0]; r += row[1]; o += row[2]; ind += row.length > 4 ? row[4] : 0; }
            double tot = Math.max(1, d + r + o);
            if (k == g.genCand(DEM)) return d / tot;
            if (k == g.genCand(REP)) return r / tot;
            if (k == g.indep()) return ind / tot;
            return g.thirdPoll(k);
        }

        static double trueMargin(Game g, int s) {
            double[][] t = truth(g);
            if (s >= 0) return Vote.margin(t[s]);
            double d = 0, r = 0, o = 0;
            for (double[] row : t) { d += row[0]; r += row[1]; o += row[2]; }
            return (d - r) / Math.max(1, d + r + o) * 100;
        }

        static boolean indRun(Game g) { Camp i = g.indep(); return i != null && i.party == IND; }

        static void weekly(Game g) {
            if (g.phase >= PH_CONVENTION || g.set.mode == MODE_GENERAL || indRun(g) && g.primaryPoll != null) general(g);
            if (g.phase <= PH_PRIMARY && g.set.mode == MODE_FULL) primary(g);
            if (g.day <= 0 || g.day == 6) Issues.record(g);
        }

        /** The main independent's share of the vote in a state (or nationally), in points. */
        static double trueInd(Game g, int s) {
            double[][] t = truth(g);
            if (s >= 0) { double[] row = t[s]; return row[4] / Math.max(1, row[0] + row[1] + row[2]) * 100; }
            double i = 0, tot = 0;
            for (double[] row : t) { i += row[4]; tot += row[0] + row[1] + row[2]; }
            return i / Math.max(1, tot) * 100;
        }

        static void general(Game g) {
            if (g.genCand(DEM) == null || g.genCand(REP) == null) return;
            g.dirty = true;
            Random r = g.rng;
            int left = ELECTION_WEEK - g.week;
            boolean daily = g.day >= 0;
            int nStates = daily ? 2 + r.nextInt(3) : left > 8 ? 5 : left > 3 ? 9 : 14;
            Camp ind = indRun(g) ? g.indep() : null;
            // national polls
            int nNat = daily ? (r.nextDouble() < 0.85 ? 1 : 0) : 2 + r.nextInt(3);
            double natTrue = trueMargin(g, -1);
            double natInd = ind != null ? trueInd(g, -1) : 0;
            for (int i = 0; i < nNat; i++) {
                int ps = r.nextInt(POLLSTERS.length);
                int n = 800 + r.nextInt(1200);
                double m = natTrue + g.natErr + g.houseEffect[ps] + r.nextGaussian() * 2 * 100 * Math.sqrt(0.25 / n);
                boolean first = g.natPubAvg == 0 && (g.week <= GENERAL_START_WEEK + 1 || g.phase <= PH_PRIMARY);
                g.natPubAvg = first ? m : g.natPubAvg * 0.8 + m * 0.2;
                if (ind != null) {
                    double mi = Math.max(0, natInd + r.nextGaussian() * 100 * Math.sqrt(0.1 / n) + g.houseEffect[ps] * 0.3);
                    g.natPubI = g.natPubI == 0 ? mi : g.natPubI * 0.8 + mi * 0.2;
                }
            }
            // state polls, mostly in competitive states
            for (int i = 0; i < nStates; i++) {
                int s = pickPolledState(g, r);
                int ps = r.nextInt(POLLSTERS.length);
                int n = 500 + r.nextInt(800);
                double m = trueMargin(g, s) + g.natErr * 0.8 + g.stateErr[s] + g.houseEffect[ps] + r.nextGaussian() * 2 * 100 * Math.sqrt(0.25 / n);
                double wOld = g.pubN[s] < 3 ? 0.5 : 0.72;
                g.pubAvg[s] = g.pubN[s] == 0 ? m : g.pubAvg[s] * wOld + m * (1 - wOld);
                InternalPoll p = new InternalPoll();
                if (ind != null) {
                    double mi = Math.max(0, trueInd(g, s) + r.nextGaussian() * 100 * Math.sqrt(0.12 / n));
                    g.pubI[s] = g.pubN[s] == 0 ? mi : g.pubI[s] * wOld + mi * (1 - wOld);
                    p.ind = mi;
                }
                g.pubN[s]++;
                p.week = g.week; p.state = s; p.n = n; p.margin = m; p.pub = true; p.pollster = POLLSTERS[ps];
                p.moe = 100 * 1.96 * Math.sqrt(0.25 / n) * 2;
                g.polls.add(p);
            }
            // states nobody polls drift toward their fundamentals
            for (int s = 0; s < NS; s++) if (g.pubN[s] == 0) {
                g.pubAvg[s] = Elect.lean[s] + natTrue + g.natErr * 0.8;
                if (ind != null) g.pubI[s] = natInd;
            }
            Camp me = g.me();
            if (me != null) {
                int w = Math.min(109, g.week);
                if (me.party == IND) me.pollHist[w] = g.natPubI;
                else if (g.phase >= PH_CONVENTION || g.set.mode == MODE_GENERAL) me.pollHist[w] = (me.party == DEM ? 1 : -1) * g.natPubAvg;
            }
        }

        static int pickPolledState(Game g, Random r) {
            double tot = 0;
            double[] w = new double[NS];
            for (int s = 0; s < NS; s++) {
                double m = Math.abs(g.pubAvg[s] != 0 ? g.pubAvg[s] : Elect.lean[s]);
                w[s] = (Math.sqrt(STATES[s].ev) + 2) * Math.exp(-m / 4.5);
                tot += w[s];
            }
            double x = r.nextDouble() * tot;
            for (int s = 0; s < NS; s++) { x -= w[s]; if (x <= 0) return s; }
            return NS - 1;
        }

        static void primary(Game g) {
            int n = g.camps.size();
            double[] d = Vote.primaryNational(g, DEM), rp = Vote.primaryNational(g, REP);
            double[] all = new double[n];
            for (int i = 0; i < n; i++) all[i] = g.camps.get(i).party == DEM ? d[i] : g.camps.get(i).party == REP ? rp[i] : 0;
            Random r = g.rng;
            if (g.primaryPoll == null || g.primaryPoll.length != n) g.primaryPoll = all.clone();
            for (int i = 0; i < n; i++) g.primaryPoll[i] = g.primaryPoll[i] * 0.5 + (all[i] + r.nextGaussian() * 0.012 * Math.sqrt(all[i] + 0.01)) * 0.5;
            for (int i = 0; i < n; i++) g.primaryPoll[i] = Math.max(0, g.primaryPoll[i]);
            Camp me = g.me();
            if (me != null) me.pollHist[Math.min(109, g.week)] = g.primaryPoll[me.idx] * 100;
        }

        /** The player (or an AI) commissions a poll. Returns null if unaffordable. */
        static InternalPoll buy(Game g, Camp k, int s, int n, int method) {
            double cost = n * POLL_COST_PER[method] * (s < 0 ? 1.3 : 1) + 6000;
            if (k.cash < cost) return null;
            Week.pay(g, k, cost, s);
            InternalPoll p = new InternalPoll();
            p.week = g.week; p.state = s; p.n = n; p.method = method; p.cost = cost; p.pollster = "Internal"; p.camp = k.idx;
            Random r = g.rng;
            double q = POLL_QUALITY[method];
            double sd = 2 * 100 * Math.sqrt(0.25 / n) / Math.sqrt(q);
            p.moe = sd * 1.96;
            boolean primary = g.phase <= PH_PRIMARY && !k.nominee;
            if (primary && k.party <= REP) {
                double[] sh = s >= 0 ? Vote.primaryShares(g, k.party, s, null) : Vote.primaryNational(g, k.party);
                p.share = new double[sh.length];
                for (int i = 0; i < sh.length; i++) p.share[i] = Math.max(0, sh[i] + r.nextGaussian() * Math.sqrt(sh[i] * (1 - sh[i]) / n) / Math.sqrt(q));
            } else {
                double m = trueMargin(g, s) + (s >= 0 ? g.stateErr[s] * 0.5 : 0) + g.natErr * 0.5 * (1.2 - q) + r.nextGaussian() * sd;
                p.margin = m;
                if (indRun(g)) p.ind = Math.max(0, trueInd(g, s) + r.nextGaussian() * 100 * Math.sqrt(0.12 / n) / Math.sqrt(q));
                for (int gr = 0; gr < NG; gr++) {
                    double size = Vote.groupSize(gr, s);
                    double gn = n * size;
                    if (gn < 40 || (method == 0 && gr >= 14 && gr <= 20 && gn < 90)) { p.groupMargin[gr] = Double.NaN; continue; }
                    p.groupMargin[gr] = Vote.groupMargin(g, gr, s) + r.nextGaussian() * 2 * 100 * Math.sqrt(0.25 / gn) / Math.sqrt(q);
                }
            }
            // issue module: most important issue and trust to handle (you vs your main rival)
            double[] mip = Issues.mip(g, s, -1);
            p.mip = new double[NI];
            for (int i = 0; i < NI; i++) p.mip[i] = Math.max(0, mip[i] + r.nextGaussian() * Math.sqrt(mip[i] * (1 - mip[i]) / n) / Math.sqrt(q));
            Camp op = Issues.rival(g, k);
            if (op != null) {
                p.trust = new double[NI];
                for (int i = 0; i < NI; i++) { double[] tr = Issues.trust(g, k, op, i, s, -1); p.trust[i] = tr[0] - tr[1] + r.nextGaussian() * sd * 0.8; }
            }
            g.polls.add(p);
            return p;
        }

        /** Latest internal poll of a campaign in a state (or null). */
        static InternalPoll latest(Game g, int camp, int s) {
            InternalPoll best = null;
            for (InternalPoll p : g.polls) if (!p.pub && p.camp == camp && p.state == s && (best == null || p.week >= best.week)) best = p;
            return best;
        }
    }

    // =====================================================================
    //  13.  EVENTS AND DECISIONS
    // =====================================================================
    static final int EV_AI = 0, EV_GRID = 1, EV_SHOOTING = 2, EV_CAMPUS = 3, EV_BORDER = 4, EV_SCOTUS = 5, EV_HURRICANE = 6,
            EV_CHINA = 7, EV_MIDEAST = 8, EV_MARKET = 9, EV_SHUTDOWN = 10, EV_ADMIN = 11, EV_CRIME = 12, EV_HEALTH = 13,
            EV_GAFFE = 14, EV_SCANDAL = 15, EV_HEALTHSCARE = 16, EV_WAR = 17, EV_OPPO = 18, NEV = 19;

    static final class Events {
        /** A decision waiting for the player: {eventId, title, body, options[], state, extra}. */
        static void ask(Game g, int ev, String title, String body, String[] options, int state) {
            g.pending.add(new Object[]{ev, title, body, options, state});
        }

        static void week(Game g) {
            Random r = g.rng;
            double rate = g.set.eventRate * (g.set.difficulty == 0 ? 0.85 : g.set.difficulty == 2 ? 1.1 : 1.0);
            int[] date = dateOf(g.week * 7 + 3);
            int month = date[1];
            boolean general = g.phase >= PH_CONVENTION;
            // the war begins
            if (g.set.scenario == SC_WAR && g.week == g.warStart && g.day <= 0) {
                g.log(NK_BIG, "WAR. China attacks Taiwan; U.S. forces are in combat across the Western Pacific. Markets plunge; the nation rallies.");
                g.newsSal[I_CHINA] += 2.0;
                g.approval += 6;
                askAll(g, EV_WAR, "America at war", "The United States is at war with China. How does your campaign respond?",
                        new String[]{"Rally behind the troops; suspend attacks for two weeks", "Back the war but demand a clear strategy and no draft", "Call for an immediate ceasefire and negotiations"});
            }
            if (g.set.scenario == SC_WAR && g.warStart >= 0 && g.week > g.warStart + 6 && r.nextDouble() < 0.08 * rate * Week.F) {
                g.log(NK_BAD, "Casualty reports and shortages mount; a draft is debated in Congress. Support for the war slips.");
                g.approval -= 1.2; g.newsSal[I_CHINA] += 0.5; g.newsSal[I_COL] += 0.2;
            }
            if (g.week - g.lastEvent < 2) { candidateEvents(g); return; }
            double p = 0.38 * rate * Week.F;
            if (r.nextDouble() < p) {
                int ev = pick(g, r, month, general);
                if (ev >= 0) { fire(g, ev); g.lastEvent = g.week; }
            }
            candidateEvents(g);
        }

        static int pick(Game g, Random r, int month, boolean general) {
            double[] w = new double[NEV];
            int sc = g.set.scenario;
            w[EV_AI] = sc == SC_AIREC ? 3 : 1.2;
            w[EV_GRID] = sc == SC_AIREC ? 1.8 : 1.0;
            w[EV_SHOOTING] = 1.0;
            w[EV_CAMPUS] = (month >= 8 || month <= 4) ? 1.0 : 0.3;
            w[EV_BORDER] = 1.0;
            w[EV_SCOTUS] = month == 5 ? 3.0 : 0;
            w[EV_HURRICANE] = month >= 7 && month <= 9 ? 2.0 : 0;
            w[EV_CHINA] = sc == SC_WAR ? 0 : 0.7;
            w[EV_MIDEAST] = 0.8;
            w[EV_MARKET] = sc == SC_BOOM ? 0.3 : 0.8;
            w[EV_SHUTDOWN] = (month == 9 && g.week < 60) ? 1.5 : 0.1;
            w[EV_ADMIN] = sc == SC_SCANDAL ? 3.0 : 0.6;
            w[EV_CRIME] = 0.7;
            w[EV_HEALTH] = month == 10 ? 1.5 : 0.5;
            double t = 0;
            for (double v : w) t += v;
            double x = r.nextDouble() * t;
            for (int i = 0; i < NEV; i++) { x -= w[i]; if (x <= 0) return i; }
            return -1;
        }

        static int randomState(Game g, String list) {
            String[] t = list.split(" ");
            return stateIdx(t[g.rng.nextInt(t.length)]);
        }

        static void fire(Game g, int ev) {
            Random r = g.rng;
            switch (ev) {
                case EV_AI: {
                    String[] co = {"a top-five bank", "a major insurer", "a Big Four accounting firm", "a household-name retailer", "a Fortune 50 tech company"};
                    int n = 4000 + r.nextInt(16) * 1000;
                    g.log(NK_EVENT, "AI LAYOFFS: " + co[r.nextInt(co.length)] + " cuts " + thousands(n) + " jobs, citing AI agents. Entry-level white-collar hiring is collapsing.");
                    g.newsSal[I_AI] += 0.5; g.newsSal[I_JOBS] += 0.6;
                    askAll(g, ev, "AI layoffs hit the headlines", "A wave of AI-driven layoffs dominates the news. What is your answer?",
                            new String[]{"Propose AI displacement insurance and a robot tax", "Build faster and retrain: AI will create more jobs", "Stay on your own message"});
                    break;
                }
                case EV_GRID: {
                    int s = randomState(g, "VA TX GA AZ OH PA IN NC");
                    g.log(NK_EVENT, s, "POWER: Electricity rates in " + STATES[s].name + " jump " + (12 + r.nextInt(14)) + "% as data centers strain the grid; a heat-wave brownout follows.");
                    g.newsSal[I_AI] += 0.4; g.newsSal[I_COL] += 0.3;
                    askAll(g, ev, "Data centers and your power bill", "Voters blame AI data centers for soaring electricity bills in " + STATES[s].name + ".",
                            new String[]{"Call for a moratorium on new hyperscale data centers", "Make data centers pay for their own power", "Defend the build-out: America must win the AI race"});
                    break;
                }
                case EV_SHOOTING: {
                    int s = r.nextInt(NS);
                    g.log(NK_EVENT, s, "TRAGEDY: A mass shooting in " + STATES[s].name + " leaves the nation grieving. Guns dominate the news cycle.");
                    g.newsSal[I_GUNS] += 0.9;
                    askAll(g, ev, "A mass shooting", "Another mass shooting. The press wants your response.",
                            new String[]{"Call for an assault-weapons ban and universal background checks", "Focus on mental health and school security", "Offer condolences; avoid the policy fight"});
                    break;
                }
                case EV_CAMPUS:
                    g.log(NK_EVENT, "CAMPUSES: Protests over Gaza and the Iran war spread across universities; arrests and accusations of antisemitism follow.");
                    g.newsSal[I_ISRAEL] += 0.7;
                    askAll(g, ev, "Campus protests", "Campus protests over the Middle East are everywhere. Where do you stand?",
                            new String[]{"Stand firmly with Israel; condemn the protests", "Call for a ceasefire and conditions on aid", "Condemn antisemitism and protect free speech; stay neutral on policy"});
                    break;
                case EV_BORDER:
                    g.log(NK_EVENT, "IMMIGRATION: " + (r.nextBoolean() ? "A border surge" : "A deadly immigration-enforcement raid") + " ignites a national firestorm.");
                    g.newsSal[I_IMMIG] += 0.7;
                    askAll(g, ev, "Immigration firestorm", "Immigration is the story of the week.",
                            new String[]{"Demand tougher enforcement and more deportations", "Condemn the enforcement tactics; defend immigrant families", "Pivot: secure the border and fix the system"});
                    break;
                case EV_SCOTUS: {
                    boolean ab = r.nextBoolean();
                    g.log(NK_EVENT, ab ? "SUPREME COURT: The Court restricts mail-order abortion pills nationwide." : "SUPREME COURT: The Court rules on birthright citizenship, upending immigration law.");
                    if (ab) g.newsSal[I_ABORT] += 1.0; else { g.newsSal[I_IMMIG] += 0.6; g.newsSal[I_DEMOC] += 0.4; }
                    askAll(g, ev, "The Supreme Court rules", ab ? "The Court has restricted access to abortion pills." : "The Court has ruled on birthright citizenship.",
                            new String[]{"Promise to overturn it by law", "Praise the ruling", "Say it is settled and move on"});
                    break;
                }
                case EV_HURRICANE: {
                    int s = randomState(g, "FL TX LA NC SC GA");
                    g.log(NK_EVENT, s, "HURRICANE: A major hurricane slams " + STATES[s].name + ". Millions without power.");
                    g.approval += r.nextBoolean() ? 1 : -1.5;
                    askAll(g, ev, "Hurricane", "A hurricane has devastated " + STATES[s].name + ". Do you change your schedule?",
                            new String[]{"Suspend campaigning for a day; visit and help", "Raise money for relief from the trail", "Keep campaigning"});
                    g.pendingState = s;
                    break;
                }
                case EV_CHINA:
                    g.log(NK_EVENT, "TAIWAN STRAIT: Chinese warships surround Taiwan in the largest exercise yet; a U.S. destroyer is buzzed.");
                    g.newsSal[I_CHINA] += 0.7;
                    askAll(g, ev, "Crisis in the Taiwan Strait", "Beijing is testing America. What do you say?",
                            new String[]{"Hawkish: warn Beijing and promise to defend Taiwan", "Calm: urge de-escalation and diplomacy", "Blame the administration's mixed signals"});
                    break;
                case EV_MIDEAST:
                    g.log(NK_EVENT, "MIDDLE EAST: Fighting flares between Israel and Iran's proxies; oil jumps and gas prices follow.");
                    g.newsSal[I_ISRAEL] += 0.4; g.newsSal[I_COL] += 0.2;
                    g.inflation += 0.1; g.approval -= 0.8;
                    break;
                case EV_MARKET:
                    g.log(NK_EVENT, "MARKETS: Stocks fall " + (6 + r.nextInt(9)) + "% in a week as the AI bubble wobbles.");
                    g.approval -= 1.4; g.newsSal[I_COL] += 0.2; g.newsSal[I_JOBS] += 0.3; g.gdp -= 0.3;
                    break;
                case EV_SHUTDOWN:
                    g.log(NK_EVENT, "SHUTDOWN: The federal government shuts down; air-traffic delays and missed paychecks follow.");
                    g.approval -= 1.2; g.newsSal[I_DEMOC] += 0.3; g.newsSal[I_COL] += 0.2;
                    break;
                case EV_ADMIN: {
                    String[] what = {"a pardon-for-cash allegation", "a crypto self-dealing probe", "an inspector general's report on a cabinet secretary",
                            "leaked texts about pressuring prosecutors", "a foreign-payments investigation"};
                    g.log(NK_EVENT, "SCANDAL: The administration is rocked by " + what[r.nextInt(what.length)] + ".");
                    g.approval -= g.set.scenario == SC_SCANDAL ? 1.8 : 1.0;
                    g.newsSal[I_DEMOC] += 0.6;
                    askAll(g, ev, "Administration scandal", "A new scandal engulfs the White House. How do you play it?",
                            new String[]{"Hammer it: corruption is the story", "Stay on the economy", "Defend the president"});
                    break;
                }
                case EV_CRIME: {
                    int s = randomState(g, "IL NY CA PA GA MI WI MN OH TX");
                    g.log(NK_EVENT, s, "CRIME: A spate of violent crime in " + STATES[s].name + " leads the cable news.");
                    g.newsSal[I_CRIME] += 0.6;
                    break;
                }
                case EV_HEALTH:
                    g.log(NK_EVENT, "HEALTH CARE: Insurance premiums for next year jump by double digits; millions face higher costs.");
                    g.newsSal[I_HEALTH] += 0.7;
                    break;
                default: break;
            }
            g.dirty = true;
        }

        /** Every active campaign answers; the player gets a dialog, the computer decides. */
        static void askAll(Game g, int ev, String title, String body, String[] options) {
            for (Camp k : g.camps) {
                if (!Week.contestant(g, k) || k.party >= GRN && k.party != IND) continue;
                if (k.human) ask(g, ev, title, body, options, -1);
                else decide(g, k, ev, AI.choose(g, k, ev, options.length));
            }
        }

        /** Applies a campaign's response to an event. */
        static void decide(Game g, Camp k, int ev, int opt) {
            boolean dem = k.party == DEM;
            String who = k.last();
            switch (ev) {
                case EV_AI:
                    if (opt == 0) { k.plat[I_JOBS] = lerp(k.plat[I_JOBS], -0.7, 0.4); k.cred[I_JOBS] += 0.05; k.imageG[24] += 0.01; shift(g, k, I_JOBS); }
                    if (opt == 1) { k.plat[I_AI] = lerp(k.plat[I_AI], 0.8, 0.4); k.cred[I_AI] += 0.04; shift(g, k, I_AI); }
                    break;
                case EV_GRID:
                    if (opt == 0) { k.plat[I_AI] = lerp(k.plat[I_AI], -0.8, 0.5); k.imageG[22] += 0.01; shift(g, k, I_AI); if (!dem) k.donorsTapped += 2e6; }
                    if (opt == 1) { k.plat[I_AI] = lerp(k.plat[I_AI], -0.3, 0.3); k.favor += 0.004; k.cred[I_COL] += 0.03; }
                    if (opt == 2) { k.plat[I_AI] = lerp(k.plat[I_AI], 0.9, 0.4); k.imageG[22] -= 0.01; k.donorsTapped -= 2e6; shift(g, k, I_AI); }
                    break;
                case EV_SHOOTING:
                    if (opt == 0) { k.plat[I_GUNS] = lerp(k.plat[I_GUNS], -0.9, 0.4); k.imageG[26] -= 0.015; k.imageG[21] += 0.006; shift(g, k, I_GUNS); }
                    if (opt == 1) { k.favor += 0.002; }
                    if (opt == 2 && dem) k.enthusiasm -= 0.01;
                    break;
                case EV_CAMPUS:
                    if (opt == 0) { k.plat[I_ISRAEL] = lerp(k.plat[I_ISRAEL], 1.0, 0.4); k.imageG[0] -= 0.01; k.imageG[18] -= 0.03; k.imageG[17] += 0.01; shift(g, k, I_ISRAEL); }
                    if (opt == 1) { k.plat[I_ISRAEL] = lerp(k.plat[I_ISRAEL], -0.6, 0.4); k.imageG[0] += 0.012; k.imageG[18] += 0.04; k.imageG[17] -= 0.015; shift(g, k, I_ISRAEL); }
                    if (opt == 2) { k.favor += 0.002; k.imageG[17] += 0.004; }
                    break;
                case EV_BORDER:
                    if (opt == 0) { k.plat[I_IMMIG] = lerp(k.plat[I_IMMIG], 0.9, 0.35); k.imageG[12] -= 0.012; shift(g, k, I_IMMIG); }
                    if (opt == 1) { k.plat[I_IMMIG] = lerp(k.plat[I_IMMIG], -0.6, 0.35); k.imageG[12] += 0.01; k.imageG[8] -= 0.008; shift(g, k, I_IMMIG); }
                    if (opt == 2) { k.plat[I_IMMIG] = lerp(k.plat[I_IMMIG], 0.1, 0.3); k.cred[I_IMMIG] += 0.03; shift(g, k, I_IMMIG); }
                    break;
                case EV_SCOTUS:
                    if (opt == 0) { k.enthusiasm += dem ? 0.02 : -0.01; k.imageG[4] += dem ? 0.01 : -0.01; }
                    if (opt == 1) { k.enthusiasm += dem ? -0.02 : 0.015; k.imageG[4] -= 0.008; }
                    break;
                case EV_HURRICANE: {
                    int s = g.pendingState;
                    if (opt == 0 && s >= 0) { k.localImage[s] += 0.02; k.favor += 0.004; k.stamina -= 6; }
                    if (opt == 1) { Week.raise(k, 1.5e6, RS_EVENTS); k.favor += 0.001; }
                    if (opt == 2 && s >= 0) { k.localImage[s] -= 0.01; }
                    break;
                }
                case EV_CHINA:
                    if (opt == 0) { k.plat[I_CHINA] = lerp(k.plat[I_CHINA], 1.0, 0.3); k.cred[I_CHINA] += 0.02 * k.a(A_FPC) / 60; shift(g, k, I_CHINA); }
                    if (opt == 1) { k.plat[I_CHINA] = lerp(k.plat[I_CHINA], -0.2, 0.3); shift(g, k, I_CHINA); }
                    if (opt == 2 && dem) { k.favor += 0.002; }
                    break;
                case EV_ADMIN:
                    if (opt == 0) { k.cred[I_DEMOC] += 0.03; if (!dem) { k.enthusiasm -= 0.02; k.imageG[9] += 0.01; if (g.phase <= PH_PRIMARY) k.favor -= 0.02; } }
                    if (opt == 2) { if (dem) k.favor -= 0.02; else { k.enthusiasm += 0.01; k.imageG[21] -= 0.01; k.cred[I_DEMOC] -= 0.02; } }
                    break;
                case EV_WAR:
                    if (opt == 0) { k.favor += 0.006; k.cred[I_CHINA] += 0.03; k.attack = 0; }
                    if (opt == 1) { k.cred[I_CHINA] += 0.02 * k.a(A_FPC) / 60; k.imageG[0] += 0.006; }
                    if (opt == 2) { k.plat[I_CHINA] = lerp(k.plat[I_CHINA], -0.5, 0.5); k.imageG[0] += 0.015; k.imageG[25] -= 0.02; k.favor -= 0.006; shift(g, k, I_CHINA); }
                    break;
                case EV_GAFFE:
                    if (opt == 0) { k.favor += 0.006; k.enthusiasm -= 0.005; }
                    if (opt == 1) { k.enthusiasm += 0.01; k.favor -= 0.006; k.imageG[21] -= 0.006; }
                    break;
                case EV_SCANDAL:
                    if (opt == 0) { k.scandalRevealed *= 0.7; k.favor -= 0.01; }
                    if (opt == 1) { if (g.rng.nextDouble() < 0.4) { k.scandalRevealed *= 1.6; g.log(NK_BAD, who + "'s denials unravel as more documents emerge."); } else k.scandalRevealed *= 0.85; }
                    if (opt == 2) { k.enthusiasm += 0.015; k.imageG[21] -= 0.01; k.imageG[9] -= 0.01; }
                    break;
                case EV_HEALTHSCARE:
                    if (opt == 0) { k.favor += 0.004; }
                    if (opt == 1) { if (g.rng.nextDouble() < 0.3) { k.favor -= 0.02; g.log(NK_BAD, "Reports contradict " + who + "'s account of the health scare."); } }
                    break;
                case EV_OPPO:
                    break;
                default: break;
            }
            g.dirty = true;
        }

        /** A promise-like stance change: off-ideology moves are tracked as flip-flops. */
        static void shift(Game g, Camp k, int issue) {
            double home = FACTIONS[k.c.faction].pos[issue];
            double d = Math.abs(k.plat[issue] - home);
            if (d > 0.45) k.flipflop += (d - 0.45) * 0.5;
        }

        static void gaffe(Game g, Camp k, int act, int s) {
            String[] g1 = {"is caught on a hot mic insulting " + (k.party == DEM ? "rural voters" : "city voters"),
                    "mangles the price of groceries", "botches a question about " + ISSUE[g.rng.nextInt(NI)].toLowerCase(),
                    "makes an off-colour joke that goes viral for the wrong reasons", "mixes up the name of the town",
                    "snaps at a voter's question on camera"};
            String what = g1[g.rng.nextInt(g1.length)];
            double sev = 0.012 + g.rng.nextDouble() * 0.02;
            k.favor -= sev;
            if (s >= 0) k.localImage[s] -= sev;
            k.attackStick[10] += 0.05;
            if (k.human || k.nominee || (g.primaryPoll != null && k.idx < g.primaryPoll.length && g.primaryPoll[k.idx] > 0.08))
                g.log(k.human ? NK_BAD : NK_INFO, s, "GAFFE: " + k.name() + " " + what + (s >= 0 ? " in " + STATES[s].name : "") + ".");
            if (k.human) ask(g, EV_GAFFE, "A gaffe", k.name() + " " + what + ". The clip is everywhere.",
                    new String[]{"Apologise quickly", "Double down: the media are out of context", "Ignore it"}, s);
            else decide(g, k, EV_GAFFE, AI.choose(g, k, EV_GAFFE, 3));
        }

        static void candidateEvents(Game g) {
            Random r = g.rng;
            for (Camp k : g.camps) {
                if (!Week.contestant(g, k) || k.party >= GRN && k.party != IND) continue;
                double exposure = k.nominee ? 2.0 : g.primaryPoll != null && k.idx < g.primaryPoll.length && g.primaryPoll[k.idx] > 0.15 ? 1.5 : 0.8;
                double p = k.a(A_SCA) / 100.0 * 0.0075 * exposure * g.set.eventRate * (1 + k.oppoOnOpponent) * Week.F;
                if (r.nextDouble() < p) scandal(g, k);
                int age = 2028 - k.c.born;
                if (age >= 69 && r.nextDouble() < 0.0025 * (age - 66) * g.set.eventRate * Week.F) {
                    g.log(k.human ? NK_BAD : NK_INFO, "HEALTH: " + k.name() + " is briefly hospitalised; questions about age and fitness follow.");
                    k.favor -= 0.01; k.attackStick[9] += 0.15;
                    if (k.human) ask(g, EV_HEALTHSCARE, "A health scare", "Your candidate was briefly hospitalised. How much do you disclose?",
                            new String[]{"Release full medical records", "Say it was dehydration and move on"}, -1);
                }
                if (r.nextDouble() < 0.012 * k.a(A_VIR) / 100.0 * g.set.eventRate * Week.F) Week.viral(g, k, "a moment on the trail");
            }
        }

        static void scandal(Game g, Camp k) {
            String[] kinds = {"undisclosed stock trades", "a former aide's harassment allegations", "a business partner under indictment",
                    "a ghost-written book deal", "donor-funded luxury travel", "old social-media posts", "a tax dispute"};
            String what = kinds[g.rng.nextInt(kinds.length)];
            double sev = 0.3 + g.rng.nextDouble() * 0.7 * k.a(A_SCA) / 100.0;
            k.scandalRevealed += sev;
            k.attributeKnown(A_SCA);
            g.log(k.human ? NK_BAD : NK_EVENT, "SCANDAL: Reports detail " + k.name() + "'s " + what + ".");
            if (k.human) ask(g, EV_SCANDAL, "A scandal breaks", "Reporters have detailed your candidate's " + what + ".",
                    new String[]{"Come clean: hold a press conference", "Deny everything", "Attack the media and the leakers"}, -1);
            else decide(g, k, EV_SCANDAL, AI.choose(g, k, EV_SCANDAL, 3));
        }

        /** The player releases opposition research on a rival. */
        static void dropOppo(Game g, Camp k, Camp target) {
            double found = target.a(A_SCA) / 100.0;
            if (g.rng.nextDouble() < 0.35 + found * 0.6) {
                target.scandalRevealed += 0.25 + found * 0.5;
                target.favor -= 0.01;
                g.log(NK_GOOD, "Your opposition research on " + target.name() + " dominates the news cycle.");
            } else {
                k.favor -= 0.01;
                k.negativity += 0.3;
                g.log(NK_BAD, "The oppo dump on " + target.name() + " fizzles and is traced back to your campaign.");
            }
            g.dirty = true;
        }
    }

    // =====================================================================
    //  14.  THE PRIMARIES, THE CONVENTIONS AND THE RUNNING MATE
    // =====================================================================
    static final int EV_VP = 30, EV_CONCEDE = 31, EV_DEBATE_DEAL = 32;

    static final class Primary {
        static void week(Game g) {
            for (Camp k : g.camps) {
                if (k.party > REP) continue;
                k.pInt = k.pInt0 * Vote.pollEdgeFade(g, k.party);
                k.inherit *= 0.97;
            }
            announcements(g);
            fortunes(g);
            trump(g);
            endorsements(g);
            contests(g);
            dropouts(g);
            clinch(g);
        }

        /** Who runs, and when they say so. */
        static void announcements(Game g) {
            if (g.week < 18) return;
            for (Camp k : g.camps) {
                if (k.human || !k.active || k.announced || k.party >= GRN) continue;
                double willing = clamp(0.45 + k.c.poll / 18.0 + (k.c.a[A_EST] + k.c.a[A_BAS]) / 600.0, 0.15, 0.97);
                boolean locked = k.c.id.equals(g.set.lockOf(k.party));
                if (k.c.id.equals("moore")) willing = 0.35;
                if (k.c.id.equals("whitmer") || k.c.id.equals("smith") || k.c.id.equals("cuban")) willing *= 0.7;
                if (k.c.id.equals("trumpjr") && g.trumpEndorse >= 0 && g.camp((int) g.trumpEndorse) != k) willing *= 0.3;
                if (k.c.id.equals("trumpjr") && vanceRunning(g)) willing *= 0.35;   // he has said he would back Vance
                if (k.c.id.equals("rubio") || k.c.id.equals("youngkin") || k.c.id.equals("kemp")) willing *= vanceRunning(g) ? 0.75 : 1.1;
                if (locked) willing = 1;
                double pAnn = 0.05 + (g.week > 34 ? 0.08 : 0);
                if (g.week > 52 && !locked) {
                    k.active = false; k.dropWeek = g.week;
                    g.log(NK_INFO, k.name() + " will not seek the " + (k.party == DEM ? "Democratic" : "Republican") + " nomination.");
                    continue;
                }
                if (g.rng.nextDouble() < pAnn) {
                    if (g.rng.nextDouble() < willing) {
                        k.announced = true; k.announceWeek = g.week;
                        k.momentum += 0.15; NameId.boost(k, 0.04);
                        Week.raise(k, (1.5e6 + 6e6 * k.a(A_SMD) / 100) * (0.5 + k.c.poll / 20), RS_SMALL);
                        g.log(NK_BIG, k.name() + " announces a run for the " + (k.party == DEM ? "Democratic" : "Republican") + " nomination.");
                    } else {
                        k.active = false; k.dropWeek = g.week;
                        g.log(NK_INFO, k.name() + " announces they will not run for president in 2028.");
                    }
                    g.dirty = true;
                }
            }
        }

        static final String[] SURGE = {"a breakout podcast appearance", "a viral town hall moment", "a string of strong interviews",
                "a much-praised policy rollout", "a wave of grassroots enthusiasm"};
        static final String[] STUMBLE = {"a staff shake-up and reports of infighting", "a disappointing fundraising quarter",
                "a widely panned interview", "questions about the campaign's direction", "a damaging profile in a national magazine"};

        /** Campaigns catch fire or fizzle for reasons no model can see: a slow random walk plus rare surges and stumbles. */
        static void fortunes(Game g) {
            if (g.phase > PH_PRIMARY) return;
            for (Camp k : g.camps) {
                if (k.human || !k.active || k.party > REP || k.nominee) continue;
                k.drift = k.drift * 0.985 + g.rng.nextGaussian() * (k.party == REP ? 0.075 : 0.07);
            }
            if (g.week < 26) return;
            for (int p = DEM; p <= REP; p++) {
                List<Camp> cs = new ArrayList<Camp>();
                for (Camp k : g.camps) if (k.party == p && k.active && k.announced && !k.human && !k.nominee) cs.add(k);
                if (cs.size() < 2 || g.primaryPoll == null) continue;
                if (g.rng.nextDouble() < 0.025 * g.set.eventRate) {
                    Camp k = cs.get(g.rng.nextInt(cs.size()));
                    if (g.primaryPoll[k.idx] < 0.25) {
                        k.drift += 0.3; k.momentum += 0.25; NameId.boost(k, 0.05);
                        g.log(NK_INFO, k.name() + " is surging after " + SURGE[g.rng.nextInt(SURGE.length)] + ".");
                    }
                }
                if (g.rng.nextDouble() < 0.015 * g.set.eventRate) {
                    Camp lead = null;
                    for (Camp k : cs) if (lead == null || g.primaryPoll[k.idx] > g.primaryPoll[lead.idx]) lead = k;
                    if (lead != null) {
                        lead.drift -= 0.22; lead.momentum -= 0.15;
                        g.log(NK_INFO, "Front-runner " + lead.name() + " stumbles amid " + STUMBLE[g.rng.nextInt(STUMBLE.length)] + ".");
                    }
                }
            }
        }

        static boolean vanceRunning(Game g) {
            for (Camp k : g.camps) if (k.c.id.equals("vance")) return k.active;
            return false;
        }

        /** The sitting president's endorsement is the most valuable asset in a Republican primary. */
        static void trump(Game g) {
            if (g.trumpEndorse >= 0 || g.week < 30 || g.week > 75) return;
            if (g.rng.nextDouble() > 0.035) return;
            Camp best = null;
            double bv = -1;
            for (Camp k : g.camps) {
                if (k.party != REP || !k.active || !k.announced) continue;
                double loyal = k.a(A_BAS) + (k.c.faction == F_MAGA || k.c.faction == F_NATCON ? 20 : 0) - (k.c.id.equals("greene") || k.c.id.equals("haley") ? 80 : 0)
                        + (k.c.id.equals("vance") ? 30 : 0);
                double v = loyal + 100 * (g.primaryPoll != null ? g.primaryPoll[k.idx] : 0) + g.rng.nextDouble() * 40;
                if (v > bv) { bv = v; best = k; }
            }
            if (best == null) return;
            if (g.rng.nextDouble() < 0.3) { g.trumpEndorse = -2; g.log(NK_INFO, "President Trump says he will stay out of the Republican primary - for now."); return; }
            g.trumpEndorse = best.idx;
            best.momentum += 0.4;
            g.log(NK_BIG, "PRESIDENT TRUMP ENDORSES " + best.name().toUpperCase() + " for the Republican nomination.");
            g.dirty = true;
        }

        static void endorsements(Game g) {
            if (g.rng.nextDouble() > 0.25) return;
            for (int e = 0; e < ENDORSERS.length; e++) {
                Endorser en = ENDORSERS[e];
                if (taken(g, e) || en.party < 0 && g.phase <= PH_PRIMARY) continue;
                if (g.phase <= PH_PRIMARY && g.week < 50) continue;
                Camp best = null;
                double bv = 0;
                for (Camp k : g.camps) {
                    if (!k.active || !k.announced || k.party != en.party) continue;
                    double v = (g.primaryPoll != null ? g.primaryPoll[k.idx] : 0.1) * 2 + k.a(A_EST) / 100.0 + Vote.groupTerm(k.c.apG, k.c.apV, 0, 0) * 0;
                    if (en.group == 24) v += -k.plat[I_JOBS] * 0.5;
                    if (en.group == 0 || en.group == 9) v += -k.plat[I_CLIMATE] * 0.4;
                    if (en.group == 26) v += k.plat[I_GUNS] * 0.5;
                    if (en.group == 6) v += k.plat[I_ABORT] * 0.6;
                    if (en.group == 17) v += (en.party == DEM ? -0.2 : 0.4) * k.plat[I_ISRAEL];
                    if (v > bv) { bv = v; best = k; }
                }
                if (best != null && g.rng.nextDouble() < 0.15) { endorse(g, best, e); return; }
            }
        }

        static boolean taken(Game g, int e) {
            for (Camp k : g.camps) if (k.endorsements.contains(e)) return true;
            return false;
        }

        static void endorse(Game g, Camp k, int e) {
            k.endorsements.add(e);
            k.momentum += 0.05;
            Endorser en = ENDORSERS[e];
            k.imageG[en.group] += en.weight * 0.2;
            g.log(k.human ? NK_GOOD : NK_INFO, en.name + " endorses " + k.name() + ".");
            g.dirty = true;
        }

        /** An endorsement meeting on the schedule. */
        static void seekEndorsement(Game g, Camp k) {
            if (g.rng.nextDouble() > 0.22 + k.a(A_EST) / 300.0) { k.c.a[A_EST] = Math.min(100, k.c.a[A_EST]); k.attr[A_EST] = Math.min(100, k.attr[A_EST] + 1); return; }
            List<Integer> open = new ArrayList<Integer>();
            for (int e = 0; e < ENDORSERS.length; e++) if (!taken(g, e) && (ENDORSERS[e].party == k.party || ENDORSERS[e].party < 0)) open.add(e);
            if (open.isEmpty()) return;
            endorse(g, k, open.get(g.rng.nextInt(open.size())));
        }

        // ---------------------------------------------------------------- contests
        static void contests(Game g) {
            double[] tmp = null;
            boolean any = false;
            for (Contest c : g.contests) {
                if (c.done || c.week != g.week) continue;
                if (g.party(c.party).isEmpty()) { c.done = true; continue; }
                any = true;
                double[] expect = g.primaryPoll != null ? g.primaryPoll.clone() : null;
                tmp = Vote.primaryShares(g, c.party, c.state, null);
                Random r = g.rng;
                double tot = 0;
                for (Camp k : g.camps) if (k.party == c.party && k.active) { tmp[k.idx] = Math.max(0.002, tmp[k.idx] * Math.exp(r.nextGaussian() * (c.caucus ? 0.22 : 0.13))); tot += tmp[k.idx]; }
                for (Camp k : g.camps) if (k.party == c.party && k.active) tmp[k.idx] /= tot; else tmp[k.idx] = 0;
                // a nominee locked in by the player wins every contest (margins still vary)
                String lockId = g.set.lockOf(c.party);
                if (lockId != null) {
                    Camp lk = null, top = null;
                    for (Camp k : g.camps) {
                        if (k.party != c.party || !k.active) continue;
                        if (k.c.id.equals(lockId)) lk = k;
                        if (top == null || tmp[k.idx] > tmp[top.idx]) top = k;
                    }
                    if (lk != null && top != null && top != lk) { double t = tmp[lk.idx]; tmp[lk.idx] = tmp[top.idx]; tmp[top.idx] = t; }
                }
                c.result = tmp;
                allocate(g, c);
                c.done = true;
                // winner, expectations and momentum
                Camp win = null;
                for (Camp k : g.camps) if (k.party == c.party && k.active && (win == null || tmp[k.idx] > tmp[win.idx])) win = k;
                int order = 0;
                for (Contest o : g.contests) if (o.party == c.party && o.week < c.week) order++;
                double weight = order < 4 ? 1.0 : order < 8 ? 0.5 : 0.2;
                for (Camp k : g.camps) {
                    if (k.party != c.party || !k.active) continue;
                    double ex = expect != null ? expect[k.idx] : 0;
                    double beat = tmp[k.idx] - ex;
                    k.momentum += clamp(beat * 2.5, -0.35, 0.5) * weight;
                    k.primaryVotes += tmp[k.idx] * Elect.stateVotes24[c.state] * 0.3;
                }
                if (win != null) {
                    win.stateWon[c.state] = true;
                    win.momentum += 0.18 * weight;
                    NameId.boost(win, 0.03 * weight);
                    String second = "";
                    Camp sec = null;
                    for (Camp k : g.camps) if (k != win && k.party == c.party && k.active && (sec == null || tmp[k.idx] > tmp[sec.idx])) sec = k;
                    if (sec != null) second = ", " + sec.last() + " " + Math.round(tmp[sec.idx] * 100) + "%";
                    g.log(NK_RESULT, c.state, (c.party == DEM ? "DEM " : "GOP ") + c.label.toUpperCase() + ": " + win.name() + " wins with " + Math.round(tmp[win.idx] * 100) + "%" + second + ".");
                }
            }
            if (any) g.dirty = true;
        }

        static void allocate(Game g, Contest c) {
            List<Camp> all = g.party(c.party);
            double[] before = new double[g.camps.size()];
            for (Camp k : all) before[k.idx] = k.delegates;
            allocate0(g, c);
            c.won = new double[g.camps.size()];
            for (Camp k : all) c.won[k.idx] = k.delegates - before[k.idx];
        }

        static void allocate0(Game g, Contest c) {
            double[] sh = c.result;
            List<Camp> cs = g.party(c.party);
            if (c.party == DEM) {
                double qual = 0;
                for (Camp k : cs) if (sh[k.idx] >= 0.15) qual += sh[k.idx];
                if (qual <= 0) { Camp top = null; for (Camp k : cs) if (top == null || sh[k.idx] > sh[top.idx]) top = k; if (top != null) top.delegates += c.delegates; return; }
                for (Camp k : cs) if (sh[k.idx] >= 0.15) k.delegates += Math.round(c.delegates * sh[k.idx] / qual);
            } else {
                Camp top = null;
                for (Camp k : cs) if (top == null || sh[k.idx] > sh[top.idx]) top = k;
                boolean early = c.week < Setup.weekOfStr("2028-03-15");
                if (top != null && (sh[top.idx] > 0.5 || (c.wta && !early))) { top.delegates += c.delegates; return; }
                double qual = 0;
                for (Camp k : cs) if (sh[k.idx] >= 0.20) qual += sh[k.idx];
                if (early) {
                    if (qual <= 0) { if (top != null) top.delegates += c.delegates; return; }
                    for (Camp k : cs) if (sh[k.idx] >= 0.20) k.delegates += Math.round(c.delegates * sh[k.idx] / qual);
                } else {
                    // winner-take-most: the plurality winner takes most district delegates
                    double rest = c.delegates * 0.35;
                    if (top != null) top.delegates += c.delegates - rest;
                    if (qual <= 0) { if (top != null) top.delegates += rest; return; }
                    for (Camp k : cs) if (sh[k.idx] >= 0.20) k.delegates += Math.round(rest * sh[k.idx] / qual);
                }
            }
        }

        /** Candidates with no money and no path quit and endorse. */
        static void dropouts(Game g) {
            int firstD = firstContest(g, DEM), firstR = firstContest(g, REP);
            for (Camp k : g.camps) {
                if (k.human || !k.active || !k.announced || k.party >= GRN || k.nominee) continue;
                double poll = g.primaryPoll != null ? g.primaryPoll[k.idx] : 0;
                int first = k.party == DEM ? firstD : firstR;
                boolean started = g.week > first;
                double total = Setup.totalDelegates(g, k.party);
                double lead = 0;
                for (Camp o : g.camps) if (o.party == k.party && o.active) lead = Math.max(lead, o.delegates);
                boolean noPath = started && (k.delegates < lead * 0.35 && remaining(g, k.party) < (lead - k.delegates) * 1.4 || poll < 0.04);
                boolean broke = k.cash < 0.6e6 && poll < 0.06 && g.week > 40;
                boolean early = !started && g.week > 48 && poll < 0.012 && g.rng.nextDouble() < 0.12;
                boolean afterEarly = started && poll < 0.08 && wins(k) == 0 && g.rng.nextDouble() < 0.35;
                if (noPath || broke || early || afterEarly) drop(g, k);
            }
        }

        static int wins(Camp k) { int n = 0; for (boolean b : k.stateWon) if (b) n++; return n; }

        static double remaining(Game g, int party) {
            double t = 0;
            for (Contest c : g.contests) if (c.party == party && !c.done) t += c.delegates;
            return t;
        }

        static int firstContest(Game g, int party) {
            int w = 999;
            for (Contest c : g.contests) if (c.party == party) w = Math.min(w, c.week);
            return w;
        }

        static void drop(Game g, Camp k) {
            if (k.party <= REP && k.c.id.equals(g.set.lockOf(k.party))) return;     // the locked nominee never quits
            double[] before = Vote.primaryNational(g, k.party);
            double share = k.idx < before.length ? before[k.idx] : 0;
            k.active = false;
            k.dropWeek = g.week;
            g.log(NK_BIG, k.name() + " suspends " + (k.c.look.female ? "her" : "his") + " campaign for the " + PARTY[k.party] + " nomination.");
            Endorse.redistribute(g, k, share);
            double[] after = Vote.primaryNational(g, k.party);
            Camp me = g.me();
            boolean bid = me != null && me.human && me.active && !me.nominee && me.party == k.party && !k.human;
            if (bid && share >= 0.004) g.pending.add(new Object[]{EV_ENDORSE, k.name() + " is out", "", new String[0], k.idx, before, after});
            else {
                Camp to = Endorse.resolve(g, k, null);
                if (me != null && me.human && me.active && me.party == k.party && share >= 0.02)
                    g.pending.add(new Object[]{EV_DROPOUT, k.name() + " is out", to == null ? "" : to.idx + "", new String[0], k.idx, before, after});
            }
            g.dirty = true;
        }

        static double needed(Game g, int party) { return Math.floor(Setup.totalDelegates(g, party) / 2) + 1; }

        static void clinch(Game g) {
            for (int party = DEM; party <= REP; party++) {
                if (g.nominee(party) != null) continue;
                List<Camp> cs = g.party(party);
                Camp lead = null;
                for (Camp k : cs) if (lead == null || k.delegates > lead.delegates) lead = k;
                boolean alone = cs.size() == 1 && g.week > firstContest(g, party);
                boolean majority = lead != null && lead.delegates >= needed(g, party);
                boolean decided = lead != null && remaining(g, party) == 0 && g.week >= (party == REP ? RNC_WEEK : DNC_WEEK);
                if (lead != null && (majority || alone)) nominate(g, lead, false);
                else if (decided) nominate(g, contested(g, party), true);
            }
        }

        static Camp contested(Game g, int party) {
            Camp best = null;
            double bv = -1;
            for (Camp k : g.party(party)) {
                double v = k.delegates * (1 + k.a(A_EST) / 250.0) + (party == REP && g.trumpEndorse == k.idx ? 300 : 0);
                if (v > bv) { bv = v; best = k; }
            }
            g.contested = true;
            g.log(NK_BIG, "CONTESTED CONVENTION: no candidate won a majority on the first ballot. On the second ballot delegates and party leaders choose " + best.name() + ".");
            return best;
        }

        static void nominate(Game g, Camp k, boolean contested) {
            k.nominee = true;
            if (k.party == DEM) { g.dNom = k.idx; g.clinchWeekD = g.week; } else { g.rNom = k.idx; g.clinchWeekR = g.week; }
            double rivals = 0;
            for (Camp o : g.camps) if (o != k && o.party == k.party && o.announced) rivals += o.smallList;
            k.smallList += 0.3 * rivals;                 // the party unites behind the nominee: lists are shared
            for (int at = 0; at < ATTACK.length; at++) k.attackStick[at] *= 0.4;   // primary attacks fade as the party unites
            for (Camp o : g.camps) if (o != k && o.party == k.party && o.active) { o.active = false; o.dropWeek = g.week; }
            k.momentum += 0.2;
            k.donorsTapped *= 0.4;
            if (!contested) g.log(NK_BIG, k.name().toUpperCase() + " CLINCHES THE " + (k.party == DEM ? "DEMOCRATIC" : "REPUBLICAN") + " NOMINATION.");
            if (k.human) {
                g.log(NK_GOOD, "You are the presumptive nominee. Choose a running mate before your convention.");
                g.pending.add(new Object[]{EV_VP, "Choose a running mate", "", new String[0], -1});
            } else {
                if (g.vpOf(k.party) == null) g.setVp(k.party, aiPickVp(g, k));
            }
            Camp me = g.me();
            if (me != null && me.party == k.party && me != k && me.human) {
                g.pending.add(new Object[]{EV_CONCEDE, "The race is over", k.name() + " has won the nomination. Your campaign is over.", new String[]{"Concede and endorse"}, -1});
            }
            g.dirty = true;
        }

        /** An independent's possible running mates: party figures who aren't on their own party's ticket, closest in outlook first. */
        static List<Cand> indVpPool(Game g, Camp k) {
            List<Cand> l = new ArrayList<Cand>();
            for (Cand c : ROSTER) {
                if (c.party > REP || c == k.c || c == g.vpD || c == g.vpR) continue;
                Camp cc = null;
                for (Camp o : g.camps) if (o.c == c || o.c.id.equals(c.id)) cc = o;
                if (cc != null && (cc.nominee || cc.active && g.phase <= PH_PRIMARY && cc.announced)) continue;
                l.add(c);
            }
            final Cand me = k.c;
            Collections.sort(l, (a, b) -> Double.compare(Math.hypot(a.econ - me.econ, a.cult - me.cult) - a.a[A_XOV] / 400.0, Math.hypot(b.econ - me.econ, b.cult - me.cult) - b.a[A_XOV] / 400.0));
            while (l.size() > 18) l.remove(l.size() - 1);
            return l;
        }

        /** The computer's choice of running mate: balance the ticket and help in a swing state. */
        static Cand aiPickVp(Game g, Camp k) {
            Cand best = null;
            double bv = -1e9;
            List<Cand> pool = k.party == IND ? indVpPool(g, k) : ROSTER;
            for (Cand c : pool) {
                if (k.party != IND && c.party != k.party || c == k.c) continue;
                double v = c.a[A_XOV] * 0.5 + c.a[A_DEB] * 0.2 + c.a[A_EST] * 0.3 + c.a[A_FPC] * 0.2 - c.a[A_SCA] * 0.5;
                if (c.home >= 0 && Math.abs(Elect.lean[c.home] + 1.5) < 6) v += 25 + STATES[c.home].ev;
                v += Math.abs(c.econ - k.c.econ) * 10;
                if (c.look.female != k.c.look.female) v += 6;
                if (2028 - c.born > 72) v -= 30;
                v += g.rng.nextDouble() * 6;
                if (v > bv) { bv = v; best = c; }
            }
            return best;
        }

        /** Phase transitions: primaries begin, summer campaign, conventions, general. */
        static void phaseCheck(Game g) {
            if (g.set.mode == MODE_GENERAL) return;
            if (g.phase == PH_INVISIBLE && g.week >= Math.min(firstContest(g, DEM), firstContest(g, REP))) {
                g.phase = PH_PRIMARY;
                g.log(NK_BIG, "The primary season begins.");
            }
            if (g.phase == PH_PRIMARY && g.dNom >= 0 && g.rNom >= 0) {
                g.phase = PH_CONVENTION;
                Camp me = g.me();
                if (me != null && !me.active && !g.spectator) { g.phase = PH_OVER; return; }
                Setup.addThirdParties(g);
                for (int p = DEM; p <= REP; p++) {
                    Camp n = g.nominee(p);
                    Week.raise(n, 20e6 + 40e6 * n.a(A_BIG) / 100, RS_PARTY);
                    n.smallList = Math.max(n.smallList, 4e6);
                }
                g.log(NK_BIG, "It's a general election: " + g.nominee(DEM).name() + " vs. " + g.nominee(REP).name() + ".");
                g.dirty = true;
                Polls.general(g);
                g.dirty = true;
            }
            if (g.phase == PH_CONVENTION && (g.week == RNC_WEEK || g.week == DNC_WEEK)) convention(g, g.week == RNC_WEEK ? REP : DEM);
            if (g.phase == PH_CONVENTION && g.week >= GENERAL_START_WEEK) {
                g.phase = PH_GENERAL;
                General.setupDebates(g);
                g.log(NK_BIG, "Labor Day: the general election sprint begins.");
                // an independent still without a running mate
                for (Camp k : g.camps) if (k.party == IND && k.active && g.vpI == null) {
                    if (k.human) g.pending.add(new Object[]{EV_VP, "Choose a running mate", "", new String[0], -1});
                    else { Cand v = aiPickVp(g, k); if (v != null) g.setVp(IND, v); }
                }
            }
            if (g.phase == PH_PRIMARY && g.week >= RNC_WEEK && (g.dNom < 0 || g.rNom < 0)) {
                for (int p = DEM; p <= REP; p++) if (g.nominee(p) == null && !g.party(p).isEmpty()) nominate(g, contested(g, p), true);
            }
        }

        static void convention(Game g, int party) {
            Camp k = g.nominee(party);
            if (k == null) return;
            if (g.vpOf(party) == null) {
                g.setVp(party, aiPickVp(g, k));
                if (k.human) g.log(NK_INFO, "Your staff chose " + g.vpOf(party).name() + " as your running mate.");
            }
            Cand vp = g.vpOf(party);
            double bounce = 0.02 + 0.03 * k.a(A_SPE) / 100 - (g.contested ? 0.015 : 0) + 0.004 * Math.min(4, k.speakers);
            k.enthusiasm += 0.008 * Math.min(4, k.speakers);
            k.favor += bounce;
            k.enthusiasm += 0.05;
            for (int s = 0; s < NS; s++) k.nameIdS[s] = Math.max(k.nameIdS[s], 0.97);
            NameId.recompute(k);
            k.smallList += 1.2e6;
            Week.raise(k, 25e6 + 30e6 * k.a(A_SMD) / 100, RS_PARTY);
            String city = party == REP ? "Houston" : "the Democratic convention city";
            g.log(NK_BIG, (party == REP ? "REPUBLICAN" : "DEMOCRATIC") + " NATIONAL CONVENTION in " + city + ": " + k.name() + " accepts the nomination" + (vp != null ? " with running mate " + vp.name() : "") + ".");
            g.dirty = true;
        }
    }

    // =====================================================================
    //  14b.  DROPOUTS: WHERE THE VOTERS GO, AND THE FIGHT FOR THE ENDORSEMENT
    // =====================================================================
    static final class Endorse {
        /** What a campaign offers a candidate who is leaving the race. */
        static final class Offer {
            List<Integer> promises = new ArrayList<Integer>();
            boolean cabinet, vp, speaking;

            boolean empty() { return promises.isEmpty() && !cabinet && !vp && !speaking; }

            String describe() {
                List<String> l = new ArrayList<String>();
                for (int p : promises) l.add("\"" + PROMISES[p].name + "\"");
                if (cabinet) l.add("a cabinet post");
                if (vp) l.add("serious consideration for vice president");
                if (speaking) l.add("a prime-time convention speech");
                if (l.isEmpty()) return "nothing in particular";
                StringBuilder b = new StringBuilder();
                for (int i = 0; i < l.size(); i++) b.append(i == 0 ? "" : i == l.size() - 1 ? " and " : ", ").append(l.get(i));
                return b.toString();
            }
        }

        static double ideoDist(Camp a, Camp b) {
            return Math.hypot(a.c.econ - b.c.econ, a.c.cult - b.c.cult) + (a.c.faction == b.c.faction ? 0 : 0.12);
        }

        /** The policy commitments the departing candidate asks for: promises squarely in their ideology that `to` hasn't made. */
        static List<Integer> asks(Game g, Camp from, Camp to, int max) {
            final double[] pos = FACTIONS[from.c.faction].pos;
            List<Integer> l = new ArrayList<Integer>();
            for (int p = 0; p < PROMISES.length; p++) {
                Promise pr = PROMISES[p];
                if (to.promises.contains(p) || Campaign.conflict(to, p) >= 0) continue;
                if (Math.abs(pr.target - pos[pr.issue]) > 0.3) continue;
                if (Math.abs(to.plat[pr.issue] - pr.target) < 0.15) continue;   // already where they want
                l.add(p);
            }
            final Game gg = g;
            Collections.sort(l, (a, b) -> Double.compare(ISSUE_SAL[PROMISES[b].issue] * gg.salience[PROMISES[b].issue] - Math.abs(PROMISES[b].target - pos[PROMISES[b].issue]) * 0.05,
                    ISSUE_SAL[PROMISES[a].issue] * gg.salience[PROMISES[a].issue] - Math.abs(PROMISES[a].target - pos[PROMISES[a].issue]) * 0.05));
            while (l.size() > max) l.remove(l.size() - 1);
            return l;
        }

        static double offerValue(Camp from, Offer o) {
            if (o == null) return 0;
            double v = 0;
            double[] pos = FACTIONS[from.c.faction].pos;
            for (int p : o.promises) v += 0.45 + 0.35 * (1 - Math.min(1, Math.abs(PROMISES[p].target - pos[PROMISES[p].issue])));
            if (o.cabinet) v += 0.45;
            if (o.vp) v += 0.9;
            if (o.speaking) v += 0.25;
            return v;
        }

        /** The departing candidate's leaning toward each remaining rival, before offers (ideology dominates). */
        static double base(Game g, Camp from, Camp to) {
            double share = g.primaryPoll != null && to.idx < g.primaryPoll.length ? g.primaryPoll[to.idx] : 0.1;
            double need = Primary.needed(g, to.party);
            return -2.2 * ideoDist(from, to) + 2.5 * share + 1.5 * Math.min(1, to.delegates / Math.max(1, need));
        }

        /** What a computer-run rival offers: trailing campaigns that need the votes bid more. */
        static Offer aiOffer(Game g, Camp from, Camp to) {
            Offer o = new Offer();
            double share = g.primaryPoll != null ? g.primaryPoll[to.idx] : 0.1;
            double fromShare = g.primaryPoll != null ? g.primaryPoll[from.idx] : 0.02;
            double need = clamp(1.0 - share * 2.2, 0, 1) * (fromShare > 0.03 ? 1 : 0.5) * (0.6 + 0.8 * g.rng.nextDouble());
            if (need < 0.25) return o;
            for (int p : asks(g, from, to, 3)) if (Campaign.offIdeology(to, p) < 0.6 && o.promises.size() < (need > 0.7 ? 2 : 1)) o.promises.add(p);
            o.speaking = true;
            o.cabinet = need > 0.5;
            o.vp = need > 0.85 && to.vpPromised == null && 2028 - from.c.born < 72;
            return o;
        }

        /** Probabilities that `from` endorses each candidate (last entry: nobody), for the odds display. */
        static double[] odds(Game g, Camp from, List<Camp> cands, Offer[] offers) {
            double[] u = new double[cands.size() + 1];
            double mx = -1e9;
            for (int i = 0; i < cands.size(); i++) { u[i] = base(g, from, cands.get(i)) + offerValue(from, offers[i]); mx = Math.max(mx, u[i]); }
            u[cands.size()] = -0.9;
            mx = Math.max(mx, u[cands.size()]);
            double z = 0;
            for (int i = 0; i < u.length; i++) { u[i] = Math.exp((u[i] - mx) / 0.45); z += u[i]; }
            for (int i = 0; i < u.length; i++) u[i] /= z;
            return u;
        }

        static List<Camp> bidders(Game g, Camp from) {
            List<Camp> cands = new ArrayList<Camp>();
            for (Camp o : g.camps) if (o != from && o.active && o.party == from.party && (o.announced || o.human)) cands.add(o);
            return cands;
        }

        /** The rivals' offers, made once when the bidding opens. */
        static Map<Integer, Offer> rivalOffers(Game g, Camp from) {
            Map<Integer, Offer> m = new HashMap<Integer, Offer>();
            for (Camp o : bidders(g, from)) if (!o.human) m.put(o.idx, aiOffer(g, from, o));
            return m;
        }

        /** Decides the endorsement, with the player's offer (or null if the player isn't bidding). */
        static Camp resolve(Game g, Camp from, Offer mine) { return resolve(g, from, mine, null); }

        static Camp resolve(Game g, Camp from, Offer mine, Map<Integer, Offer> ai) {
            List<Camp> cands = bidders(g, from);
            if (cands.isEmpty()) return null;
            Offer[] offers = new Offer[cands.size()];
            for (int i = 0; i < cands.size(); i++) {
                Camp c = cands.get(i);
                offers[i] = c.human ? (mine == null ? new Offer() : mine) : ai != null && ai.containsKey(c.idx) ? ai.get(c.idx) : aiOffer(g, from, c);
            }
            double[] p = odds(g, from, cands, offers);
            double x = g.rng.nextDouble();
            int pick = p.length - 1;
            for (int i = 0; i < p.length; i++) { x -= p[i]; if (x <= 0) { pick = i; break; } }
            if (pick >= cands.size()) {
                g.log(NK_BIG, from.name() + " ends " + (from.c.look.female ? "her" : "his") + " campaign without endorsing anyone.");
                return null;
            }
            Camp to = cands.get(pick);
            apply(g, from, to, offers[pick]);
            for (int i = 0; i < cands.size(); i++) if (cands.get(i).human && i != pick && mine != null && !mine.empty())
                g.log(NK_BAD, from.last() + " turns down your offer.");
            return to;
        }

        static void apply(Game g, Camp from, Camp to, Offer o) {
            double share = g.primaryPoll != null ? g.primaryPoll[from.idx] : 0.02;
            to.momentum += 0.08 + share;
            to.delegates += from.delegates * 0.5;
            from.delegates *= 0.5;
            for (int i = 0; i < from.c.apG.length; i++) to.imageG[from.c.apG[i]] += from.c.apV[i] * 0.1;
            // an extra slice of the departing candidate's voters follows the endorsement
            double toShare = g.primaryPoll != null ? Math.max(0.01, g.primaryPoll[to.idx]) : 0.1;
            to.inherit += Vote.PRIMARY_TEMP * Math.log(1 + 0.2 * share / toShare);
            String deal = o == null || o.empty() ? "" : " after winning commitments to " + o.describe();
            g.log(to.human ? NK_GOOD : NK_BIG, from.name() + " endorses " + to.name() + deal + ".");
            if (o != null) {
                for (int p : o.promises) Campaign.promise(g, to, p);
                if (o.cabinet) to.deals.add("Cabinet post for " + from.name());
                if (o.vp) { to.vpPromised = from.c.id; to.deals.add("Vice-presidential consideration for " + from.name()); }
                if (o.speaking) { to.speakers++; to.deals.add("Convention speech for " + from.name()); }
            }
            g.dirty = true;
        }

        /**
         * Most of a departing candidate's support goes to ideological neighbours; the rest spreads by current standing.
         * Implemented as a small utility transfer ('inherit') solved so the national poll moves that way.
         */
        static void redistribute(Game g, Camp k, double share) {
            if (share < 0.003 || k.party > REP) return;
            List<Camp> rest = new ArrayList<Camp>();
            for (Camp o : g.camps) if (o != k && o.active && o.party == k.party) rest.add(o);
            if (rest.isEmpty()) return;
            double[] cur = Vote.primaryNational(g, k.party);
            double sw = 0, sc = 0;
            double[] w = new double[rest.size()];
            for (int i = 0; i < rest.size(); i++) { w[i] = Math.exp(-ideoDist(k, rest.get(i)) / 0.3); sw += w[i]; sc += cur[rest.get(i).idx]; }
            double[] target = new double[rest.size()];
            double tot = 0;
            for (int i = 0; i < rest.size(); i++) {
                Camp o = rest.get(i);
                double before = g.primaryPoll != null ? g.primaryPoll[o.idx] : cur[o.idx];
                target[i] = before + share * (0.65 * w[i] / sw + 0.35 * cur[o.idx] / Math.max(1e-6, sc));
                tot += target[i];
            }
            for (int i = 0; i < rest.size(); i++) target[i] /= Math.max(1e-6, tot);
            for (int it = 0; it < 3; it++) {
                double[] sh = Vote.primaryNational(g, k.party);
                for (int i = 0; i < rest.size(); i++) {
                    Camp o = rest.get(i);
                    o.inherit += 0.6 * Vote.PRIMARY_TEMP * Math.log(Math.max(1e-4, target[i]) / Math.max(1e-4, sh[o.idx]));
                }
            }
        }
    }

    // =====================================================================
    //  15.  THE GENERAL ELECTION: debates, early voting, election night
    // =====================================================================
    static final class General {
        /** Share of each state's votes cast before Election Day, by week of the general (from week 97). */
        static double earlyShare(int s) {
            String ab = STATES[s].ab;
            if ("WA OR CO UT NV HI VT CA".contains(ab)) return 0.82;
            if ("AZ".equals(ab)) return 0.78;
            if ("GA NC TX FL TN NM MT".contains(ab)) return 0.66;
            if ("MI WI MN PA VA MD NJ".contains(ab)) return 0.48;
            if ("NY OH IA".contains(ab)) return 0.40;
            if ("MS AL NH CT MO KY LA DE".contains(ab)) return 0.20;
            return 0.38;
        }

        static void week(Game g) {
            if (g.week >= EARLY_VOTE_WEEK && g.week < ELECTION_WEEK) bank(g);
            if (g.week == ELECTION_WEEK) election(g);
        }

        /** Early and mail votes are locked in each week of early voting. */
        static void bank(Game g) {
            double[][] t = Vote.general(g, null, null);
            int weeks = ELECTION_WEEK - EARLY_VOTE_WEEK;
            int i = g.week - EARLY_VOTE_WEEK;
            double ramp = (i + 1.0) / (weeks * (weeks + 1) / 2.0) * Week.F;       // early voting accelerates toward the end
            for (int s = 0; s < NS; s++) {
                double f = earlyShare(s) * ramp;
                g.banked[s][0] += t[s][0] * f;
                g.banked[s][1] += t[s][1] * f;
                g.banked[s][2] += t[s][2] * f;
                if (g.banked[s].length > 3 && t[s].length > 4) g.banked[s][3] += t[s][4] * f;
            }
        }

        static double bankedFrac(Game g, int s, double[][] t) {
            double b = g.banked[s][0] + g.banked[s][1] + g.banked[s][2];
            double tot = t[s][0] + t[s][1] + t[s][2];
            return tot <= 0 ? 0 : Math.min(0.95, b / tot);
        }

        /** Election Day: banked votes plus the rest at today's preferences. */
        static void election(Game g) {
            double[][] t = Vote.general(g, null, null);
            g.result = new double[NS][4];
            double[] m = new double[NS];
            g.popD = g.popR = g.popO = g.popI = 0;
            for (int s = 0; s < NS; s++) {
                double f = bankedFrac(g, s, t);
                double noise = g.rng.nextGaussian() * 0.6;
                for (int j = 0; j < 3; j++) g.result[s][j] = g.banked[s][j] + t[s][j] * (1 - f);
                double bi = g.banked[s].length > 3 ? g.banked[s][3] : 0;
                g.result[s][3] = bi + t[s][4] * (1 - f);
                double tot = g.result[s][0] + g.result[s][1] + g.result[s][2];
                g.result[s][0] += tot * noise / 200; g.result[s][1] -= tot * noise / 200;
                m[s] = Vote.margin(g.result[s]);
                g.popD += g.result[s][0]; g.popR += g.result[s][1]; g.popO += g.result[s][2]; g.popI += g.result[s][3];
            }
            int[] ev = Vote.electoral3(g.result, 3, g.cdWinner);
            g.evD = ev[0]; g.evR = ev[1]; g.evI = ev[2];
            senate(g, m);
            house(g);
            if (Math.max(g.evD, Math.max(g.evR, g.evI)) < 270) g.contingent = contingent(g);
            g.phase = PH_OVER;
            g.dirty = true;
        }

        /** The party (DEM, REP or IND) that wins the presidency. */
        static int winnerParty(Game g) {
            if (g.contingent >= 0) return g.contingent;
            return g.evI >= 270 ? IND : g.evD >= g.evR ? DEM : REP;
        }

        /** House of Representatives: the national House vote follows the presidential vote with ticket-splitting and noise. */
        static void house(Game g) {
            double tot = Math.max(1, g.popD + g.popR + g.popO);
            double pres = (g.popD - g.popR) / tot * 100;
            double m = 0.82 * pres + 0.18 * g.set.midGeneric * 0.5 + g.senateNoise[0] * 0.4;
            g.houseMargin = m;
            g.houseSeatsD = (int) Math.round(clamp(217.5 + 6.2 * (m - 1.2), 160, 300));
        }

        /**
         * No one reached 270: the new House picks the president, one vote per state delegation (12th Amendment).
         * Delegations are estimated from each state's partisan lean and the national House vote.
         */
        static int contingent(Game g) {
            int d = 0, r = 0;
            for (int s = 0; s < NS; s++) {
                if (STATES[s].ab.equals("DC")) continue;
                double lean = Elect.lean[s] + g.houseMargin - 1.5;
                if (lean > 0) d++; else r++;
            }
            g.log(NK_BIG, "No candidate won 270 electoral votes. The House of Representatives will choose the president, one vote per state delegation: " + d + " Democratic delegations, " + r + " Republican.");
            return d >= 26 ? DEM : r >= 26 ? REP : (g.evD >= g.evR ? DEM : REP);
        }

        static void senate(Game g, double[] m) {
            for (SenateRace r : g.senate) {
                double inc = r.open ? 0 : (r.party == DEM ? 3.0 : -3.0);
                if (r.note.contains("independent")) inc *= 1.8;
                double split = (r.base - (Elect.lean[r.state] + 0)) * 0.25;
                r.margin = m[r.state] * 0.85 + inc + split + g.senateNoise[r.state];
            }
        }

        static int senateSeats(Game g) {
            int d = 0;
            for (SenateRace r : g.senate) if (r.margin > 0) d++;
            int heldD = 0;
            for (SenateRace r : g.senate) if (r.party == DEM) heldD++;
            return g.set.midSenateD - heldD + d;            // seats not up in 2028 plus the seats won
        }

        // ---------------------------------------------------------------- debates
        /** Default schedule: what the commission-style calendar would offer. */
        static void setupDebates(Game g) {
            for (Debate d : g.debates) if (d.party < 0) return;
            int n = g.debateDeal[0];
            int first = 96 + g.debateDeal[1];
            int[] hostsPlan = {0, 2, 3, 1};
            for (int i = 0; i < n; i++) {
                Debate d = new Debate();
                d.party = -1; d.week = Math.min(ELECTION_WEEK - 1, first + i * 2); d.host = (g.debateDeal[3] + hostsPlan[i % 4]) % 6;
                d.mutedMics = true;
                g.debates.add(d);
            }
            if (g.debateDeal[2] == 1) {
                Debate vp = new Debate();
                vp.party = -1; vp.vp = true; vp.week = Math.min(ELECTION_WEEK - 2, first + 1); vp.host = 1;
                g.debates.add(vp);
            }
            if (n == 0) g.log(NK_BAD, "There will be no presidential debates this year.");
            else g.log(NK_INFO, "Debate schedule set: " + n + " presidential debate" + (n > 1 ? "s" : "") + (g.debateDeal[2] == 1 ? " and a vice-presidential debate" : "") + ", beginning " + weekStr(first) + ".");
        }

        /** What the computer opponent wants from debate negotiations. */
        static int[] aiDebateAsk(Game g, Camp ai, Camp me) {
            double lead = (ai.party == DEM ? 1 : -1) * Polls.trueMargin(g, -1);
            double skill = ai.a(A_DEB) + ai.a(A_CHA) * 0.5 - me.a(A_DEB) - me.a(A_CHA) * 0.5;
            int count = (int) Math.round(clamp(2.4 - lead * 0.25 + skill * 0.03, 0, 4));
            int timing = lead > 2 ? 3 : lead < -2 ? 0 : 1;
            int vp = 1;
            int host = ai.party == DEM ? 0 : 4;
            return new int[]{count, timing, vp, host};
        }

        /** Player proposes; the computer accepts, counters or refuses. Returns {count, timing, vp, host, verdict}. */
        static int[] negotiate(Game g, int[] mine, boolean insist) {
            Camp me = g.me(), ai = g.opp();
            int[] ask = aiDebateAsk(g, ai, me);
            int[] deal = new int[5];
            double flex = 0.5 + (ai.party == DEM ? 1 : -1) * -Polls.trueMargin(g, -1) * 0.05;
            for (int i = 0; i < 4; i++) deal[i] = ask[i];
            deal[0] = (int) Math.round((mine[0] + ask[0]) / 2.0);
            deal[1] = (int) Math.round((mine[1] + ask[1]) / 2.0);
            deal[2] = mine[2] == ask[2] ? mine[2] : 1;
            deal[3] = mine[3] == ask[3] ? mine[3] : 5;          // disagreement on hosts: a neutral network
            if (insist) {
                if (g.rng.nextDouble() < clamp(flex, 0.15, 0.85)) { System.arraycopy(mine, 0, deal, 0, 4); deal[4] = 1; }
                else { deal[4] = -1; }
            } else deal[4] = 0;
            return deal;
        }
    }

    // =====================================================================
    //  16.  DEBATES  -  segments, tactics, the live dial and the spin room
    // =====================================================================
    static final String[] TACTIC = {"Policy depth", "Attack", "Pivot to message", "Personal story", "Zinger", "Defend the record"};
    static final String[] TACTIC_DESC = {
            "A detailed, command-of-the-facts answer. Rewards intellect and preparation; beats a pivot.",
            "Go after your opponent. Rewards debating skill and charisma; beats a policy answer, but backfires if overused.",
            "Bridge to your weekly message. Rewards discipline; deflects attacks.",
            "Tell a human story. Rewards authenticity and charisma; blunts attacks.",
            "Fire a prepared one-liner. High risk, high reward - needs preparation; can go viral either way.",
            "Defend your record on the issue. Rewards intellect and executive record."};

    /** Policy positions by issue, as a candidate would describe them on stage (left pole, right pole). */
    static final String[][] POLICY_PHRASE = {
            {"compete with China without a new Cold War: rebuild alliances and trade on our terms", "confront Beijing: tariffs, export controls and a Navy strong enough to deter"},
            {"put guardrails on AI and make data centers pay their own way on power", "win the AI race: build the data centers and the power plants to run them"},
            {"bring costs down: cap prices on essentials and take on corporate price-gouging", "cut spending and red tape and unleash American energy to bring prices down"},
            {"protect workers displaced by AI with wage insurance and retraining", "let the market create the jobs and get government out of the way"},
            {"lower prices by rolling back blanket tariffs while protecting key industries", "protect American workers with tariffs on countries that cheat"},
            {"condition aid on humanitarian law and push hard for a lasting ceasefire", "stand with Israel without conditions and keep the pressure on Iran"},
            {"cap rent hikes and build public and affordable housing", "build, build, build: cut the red tape and zoning so supply catches up"},
            {"secure the border and create a path to citizenship", "finish the wall and deport those here illegally"},
            {"restore Roe nationwide", "protect life and let the states decide"},
            {"a public option and caps on drug costs for every family", "more choice and competition, less government in health care"},
            {"expand Social Security and pay for it by lifting the payroll-tax cap", "save Social Security for the next generation with reforms"},
            {"invest in prevention and accountability in policing", "back the police and get tough on violent crime"},
            {"build the clean-energy economy and the jobs that come with it", "drill here, drill now and end the EV mandates"},
            {"ban congressional stock trading and rein in presidential power", "drain the swamp and take on the deep state"},
            {"universal background checks and a ban on weapons of war", "defend the Second Amendment and go after criminals"},
            {"make college affordable and cancel crushing student debt", "school choice for every family and no bailouts for elites"},
            {"stand with Ukraine until Russia withdraws", "negotiate an end to the war and stop the blank checks"},
            {"protect transgender Americans from discrimination", "keep males out of girls' sports and protect kids"}};
    /** Personal stories a candidate can tell on each issue. */
    static final String[] STORY = {"a factory town that lost its jobs to China", "neighbors' power bills after a data center came to town",
            "what groceries cost the family growing up", "a friend whose job was automated away", "a farmer caught in the trade war",
            "families on both sides who lost loved ones", "a first apartment where rent ate half the paycheck", "an immigrant family and a border town on the trail",
            "a woman denied care in an emergency", "a medical bill that nearly broke the family", "a parent's retirement", "a police officer and a crime victim met on the trail",
            "the storm that hit home", "why public service, and what it should mean", "a community after a shooting", "student loans - and the kids' school",
            "veterans and families who know the cost of war", "a family met on the trail"};
    /** Attack lines in a debater's words (index by ATTACK). */
    static final String[] ATTACK_PHRASE = {"", "too extreme for this country", "out of touch with working people", "bought and paid for by big donors",
            "not ready for the job", "a puppet of the party's fringe", "someone who says whatever the room wants", "weak on crime and the border",
            "coming for your Social Security", "past their prime", "all chaos and no plan"};
    static final String[] REBUT = {"Counterattack", "Defend your record", "Laugh it off", "Ignore it and pivot"};
    static final String[] REBUT_DESC = {"Hit back harder. Debating and charisma; high risk, high reward.", "Correct the record with facts. Intellect and your record; steady.",
            "Disarm it with humour. Charisma and authenticity; works best against weak shots.", "Stay on message and don't take the bait. Discipline; small but safe."};

    /** A written answer the player can give: the tactic, the line, its target and how risky it is. */
    static final class Line {
        String text, tag;
        int tactic, target = -1;
        double bonus, sd;
    }

    static final class DebateRun {
        Debate d;
        List<Camp> parts = new ArrayList<Camp>();
        Cand[] vpCands;
        int seg, nSeg;
        int[] topics;
        double[] score, last;
        int[] lastTactic, attacks, zingers;
        List<String> lines = new ArrayList<String>();
        List<double[]> dial = new ArrayList<double[]>();
        boolean over;
        String headline;
        int myIdx = -1;
        // the player's turn: choose a tactic, then a line; answer rebuttals when attacked
        int pickTactic = -1;
        Line[] options;
        int rebutBy = -1, rebutAttack;
        double rebutPower;
    }

    static final class Debates {
        static List<Debate> thisWeek(Game g) {
            List<Debate> l = new ArrayList<Debate>();
            for (Debate d : g.debates) if (!d.done && d.week == g.week && (g.day < 0 || g.day >= 2)) l.add(d);   // in the daily stretch, debates fall on Tuesday
            return l;
        }

        /** Who qualifies for a primary debate (poll threshold), capped at ten. */
        static List<Camp> qualifiers(Game g, Debate d) {
            List<Camp> l = new ArrayList<Camp>();
            if (d.party < 0) {
                if (d.vp) { l.add(g.nominee(DEM)); l.add(g.nominee(REP)); return l; }
                l.add(g.nominee(DEM)); l.add(g.nominee(REP));
                for (Camp k : g.camps) if (k.party >= GRN && k.active && g.thirdPoll(k) >= 0.15) l.add(k);
                return l;
            }
            for (Camp k : g.camps) {
                if (k.party != d.party || !k.active || !k.announced) continue;
                double p = g.primaryPoll != null ? g.primaryPoll[k.idx] * 100 : 0;
                if (p >= d.thresholdPoll || k.human && p >= d.thresholdPoll * 0.7) l.add(k);
            }
            final double[] pp = g.primaryPoll;
            Collections.sort(l, new Comparator<Camp>() { public int compare(Camp a, Camp b) { return pp == null ? 0 : Double.compare(pp[b.idx], pp[a.idx]); } });
            while (l.size() > 10) l.remove(l.size() - 1);
            return l;
        }

        static DebateRun start(Game g, Debate d) {
            DebateRun r = new DebateRun();
            r.d = d;
            r.parts = qualifiers(g, d);
            d.participants.clear();
            for (Camp k : r.parts) d.participants.add(k.idx);
            if (d.vp) {
                for (int p = DEM; p <= REP; p++) if (g.vpOf(p) == null && g.nominee(p) != null) g.setVp(p, Primary.aiPickVp(g, g.nominee(p)));
                r.vpCands = new Cand[]{g.vpD, g.vpR};
            }
            int n = r.parts.size();
            r.nSeg = d.vp ? 6 : d.party < 0 ? 8 : n <= 3 ? 7 : n <= 6 ? 6 : 5;
            r.score = new double[n]; r.last = new double[n]; r.lastTactic = new int[n]; r.attacks = new int[n]; r.zingers = new int[n];
            Arrays.fill(r.lastTactic, -1);
            // topics: drawn from all eighteen issues, weighted by how much voters care right now (no repeats)
            r.topics = new int[r.nSeg];
            boolean[] used = new boolean[NI];
            for (int i = 0; i < r.nSeg; i++) {
                double tot = 0;
                double[] w = new double[NI];
                for (int t = 0; t < NI; t++) if (!used[t]) { w[t] = Math.pow(ISSUE_SAL[t] * g.salience[t], 0.8); tot += w[t]; }
                double x = g.rng.nextDouble() * tot;
                int pick = 0;
                for (int t = 0; t < NI; t++) { if (used[t]) continue; x -= w[t]; pick = t; if (x <= 0) break; }
                used[pick] = true;
                r.topics[i] = pick;
            }
            for (int i = 0; i < n; i++) if (r.parts.get(i).human) r.myIdx = i;
            String who = d.vp ? "the vice-presidential candidates" : n == 2 ? r.parts.get(0).last() + " and " + r.parts.get(1).last() : n + " candidates";
            r.lines.add(HOSTS[d.host] + " hosts " + (d.party < 0 ? "the debate" : "the " + PARTY[d.party] + " primary debate") + " with " + who + ".");
            if (r.myIdx < 0) { while (!r.over) segment(g, r, -1); }
            return r;
        }

        static Cand who(DebateRun r, int i) { return r.vpCands != null ? r.vpCands[i] : r.parts.get(i).c; }

        static double att(DebateRun r, int i, int a) {
            if (r.vpCands != null) return r.vpCands[i] == null ? 50 : r.vpCands[i].a[a];
            return r.parts.get(i).a(a);
        }

        static double skill(DebateRun r, int i, int t) {
            switch (t) {
                case 0: return 0.6 * att(r, i, A_INT) + 0.2 * att(r, i, A_DEB) + 0.2 * att(r, i, A_DIS);
                case 1: return 0.5 * att(r, i, A_DEB) + 0.3 * att(r, i, A_CHA) + 0.2 * att(r, i, A_VIR);
                case 2: return 0.6 * att(r, i, A_DIS) + 0.4 * att(r, i, A_SPE);
                case 3: return 0.5 * att(r, i, A_AUT) + 0.5 * att(r, i, A_CHA);
                case 4: return 0.5 * att(r, i, A_VIR) + 0.3 * att(r, i, A_CHA) + 0.2 * att(r, i, A_DEB);
                default: return 0.5 * att(r, i, A_INT) + 0.5 * att(r, i, A_EXE);
            }
        }

        /** The computer's choice of tactic. */
        static int aiTactic(Game g, DebateRun r, int i) {
            double[] v = new double[TACTIC.length];
            for (int t = 0; t < v.length; t++) v[t] = skill(r, i, t) + g.rng.nextGaussian() * 8;
            if (r.attacks[i] >= 2) v[1] -= 15;
            Camp k = r.parts.get(i);
            if (k.prep < 20 || r.zingers[i] >= 1) v[4] -= 30;
            int last = -1;
            for (int j = 0; j < r.parts.size(); j++) if (j != i && r.lastTactic[j] == 1) last = 1;
            if (last == 1) { v[2] += 6; v[3] += 4; v[5] += 5; }
            int best = 0;
            for (int t = 1; t < v.length; t++) if (v[t] > v[best]) best = t;
            return best;
        }

        static double poll(Game g, Camp k) { return g.primaryPoll != null && k.idx < g.primaryPoll.length ? g.primaryPoll[k.idx] : 0.3; }

        /** Rivals on stage worth engaging, strongest first (indices into r.parts). */
        static List<Integer> rivals(Game g, DebateRun r, int me) {
            List<Integer> l = new ArrayList<Integer>();
            for (int i = 0; i < r.parts.size(); i++) if (i != me) l.add(i);
            final DebateRun rr = r;
            final Game gg = g;
            Collections.sort(l, (a, b) -> Double.compare(poll(gg, rr.parts.get(b)), poll(gg, rr.parts.get(a))));
            return l;
        }

        static Line line(String text, String tag, int tactic, double bonus, double sd, int target) {
            Line l = new Line();
            l.text = text; l.tag = tag; l.tactic = tactic; l.bonus = bonus; l.sd = sd; l.target = target;
            return l;
        }

        /** Written answers for a tactic on the current question, built from the platform, record and rivals' weak spots. */
        static Line[] lines(Game g, DebateRun r, int t) {
            int me = r.myIdx;
            Camp k = r.parts.get(me);
            int topic = r.topics[Math.min(r.seg, r.nSeg - 1)];
            String iss = ISSUE[topic].toLowerCase();
            List<Integer> riv = rivals(g, r, me);
            List<Line> l = new ArrayList<Line>();
            double cred = k.cred[topic] / Issues.CRED_CAP;
            boolean focus = Issues.inFocus(k, topic);
            switch (t) {
                case 0: {
                    String plan = POLICY_PHRASE[topic][k.plat[topic] < 0 ? 0 : 1];
                    l.add(line("Lay out your plan: " + plan + ".", "Safe", 0, 2 + cred * 6 + (focus ? 3 : 0), 4, -1));
                    l.add(line("Walk through the numbers on " + iss + ": what it costs, how fast it works and who pays.", "Detailed", 0, (k.a(A_INT) - 70) * 0.35, 7, -1));
                    if (!riv.isEmpty()) { int o = riv.get(0); l.add(line("Contrast your " + iss + " plan with " + Debates.who(r, o).last + "'s - point by point.", "Contrast", 0, 2 + cred * 4, 8, o)); }
                    break;
                }
                case 1: {
                    java.util.Set<Integer> used = new java.util.HashSet<Integer>();
                    for (int j = 0; j < Math.min(3, riv.size()); j++) {
                        int o = riv.get(j);
                        Camp t2 = r.parts.get(o);
                        String who = Debates.who(r, o).last;
                        double gap = Math.abs(t2.plat[topic] - k.plat[topic]);
                        if (j == 1 && gap > 0.3) {
                            boolean left = t2.plat[topic] < k.plat[topic];
                            String text = "Hit " + who + " on " + iss + ": " + (left ? "\"" + POLICY_PHRASE[topic][0] + "\" sounds good until families get the bill." : "\"" + POLICY_PHRASE[topic][1] + "\" leaves working people on their own.");
                            l.add(line(text, "Policy contrast", 1, 1 + gap * 5 + cred * 3, 8, o));
                            continue;
                        }
                        int best = -1;
                        for (int a = 1; a < ATTACK.length; a++) if (!used.contains(a) && (best < 0 || Message.attackFit(g, t2, a) > Message.attackFit(g, t2, best))) best = a;
                        if (best < 0) best = 1;
                        used.add(best);
                        double fit = Message.attackFit(g, t2, best);
                        String text = (j == 0 ? "Take on the front-runner. " : "") + "Go after " + who + ": " + ATTACK_PHRASE[best] + ".";
                        l.add(line(text, j == 0 ? "Front-runner" : fit > 0.7 ? "Sharp" : "Long shot", 1, fit * 10 - 3, 9, o));
                    }
                    break;
                }
                case 2: {
                    int f = clampi(k.frame, 0, FRAME.length - 1);
                    l.add(line("Bridge to your message: " + FRAME[f].toLowerCase() + ".", "On message", 2, k.frameStr[f] * 5 + (k.a(A_DIS) - 70) * 0.15, 5, -1));
                    if (k.focusIssue != topic) l.add(line("Pivot to your main issue, " + ISSUE[k.focusIssue].toLowerCase() + " - where voters trust you.", "Your issue", 2, k.cred[k.focusIssue] / Issues.CRED_CAP * 7 - 1, 6, -1));
                    l.add(line("Acknowledge the question in a sentence, then speak straight to the camera about what this election is really about.", "Big picture", 2, (k.a(A_SPE) - 70) * 0.25, 7, -1));
                    break;
                }
                case 3: {
                    l.add(line("Tell the story of " + STORY[topic] + ".", "Personal", 3, (k.a(A_AUT) - 60) * 0.2 + (focus ? 2 : 0), 6, -1));
                    l.add(line("Talk about a voter you met in " + STATES[Math.max(0, k.location)].name + " and what they asked you to fix.", "Trail story", 3, (k.a(A_RET) - 60) * 0.15 + 1, 5, -1));
                    break;
                }
                case 4: {
                    if (!riv.isEmpty()) {
                        int o = riv.get(0);
                        int best = Digital.bestAttack(g, r.parts.get(o));
                        l.add(line("Fire the line your team wrote about " + Debates.who(r, o).last + " being " + ATTACK_PHRASE[best] + ".", "Big swing", 4, 3 + Message.attackFit(g, r.parts.get(o), best) * 4, 16, o));
                    }
                    l.add(line("A self-deprecating joke to turn the question around, then one crisp sentence on " + iss + ".", "Charm", 4, (k.a(A_CHA) - 65) * 0.25, 10, -1));
                    break;
                }
                default: {
                    String rec = null;
                    double rc = 0;
                    for (int i = 0; i < k.c.record.length; i++) if (k.c.recIssue[i] == topic) { rec = k.c.record[i]; rc = k.c.recCred[i]; }
                    if (rec != null) l.add(line("Point to your record: " + Character.toLowerCase(rec.charAt(0)) + rec.substring(1) + (rec.endsWith(".") ? "" : "."), "Record", 5, 3 + rc * 40, 5, -1));
                    l.add(line("Point to what you've done as " + k.c.role + " and who it helped.", "Experience", 5, (k.a(A_EXE) - 60) * 0.2, 5, -1));
                    if (k.cred[topic] > 0.05) l.add(line("Own the hard parts of your record on " + iss + " - and explain what you learned.", "Candid", 5, (k.a(A_AUT) - 60) * 0.15 + cred * 4, 8, -1));
                    break;
                }
            }
            return l.toArray(new Line[0]);
        }

        /** Plays one segment. myTactic is the player's choice (or -1 if the player is not on stage). */
        static void segment(Game g, DebateRun r, int myTactic) { segment(g, r, myTactic, null); }

        static void segment(Game g, DebateRun r, int myTactic, Line myLine) {
            if (r.rebutBy >= 0) { rebut(g, r, aiRebut(r)); if (r.over) return; }
            if (r.seg >= r.nSeg) { finish(g, r); return; }
            int n = r.parts.size();
            int topic = r.topics[r.seg];
            r.lines.add("");
            r.lines.add("Q" + (r.seg + 1) + "  " + ISSUE[topic].toUpperCase());
            int[] tac = new int[n];
            if (myLine != null) myTactic = myLine.tactic;
            for (int i = 0; i < n; i++) tac[i] = i == r.myIdx && myTactic >= 0 ? myTactic : aiTactic(g, r, i);
            boolean big = n <= 3;
            double[] seg = new double[n];
            int[] target = new int[n];
            Arrays.fill(target, -1);
            for (int i = 0; i < n; i++) {
                if (!big && g.rng.nextDouble() < 0.45 && i != r.myIdx) { seg[i] = Double.NaN; continue; }   // crowded stage: not everyone is called on
                Camp k = r.parts.get(i);
                boolean mine = i == r.myIdx && myLine != null;
                double sd = mine ? myLine.sd : (tac[i] == 4 ? 16 : 7);
                double s = skill(r, i, tac[i]) + Math.min(40, k.prep) * 0.28 + g.rng.nextGaussian() * sd + (mine ? myLine.bonus : 0);
                if (Issues.inFocus(k, topic)) s += 2;
                if (tac[i] == 1 || tac[i] == 4) target[i] = mine ? myLine.target : aiTarget(g, r, i);
                // issue fit: credibility and a position near the audience's
                double med = r.d.party == DEM ? IDEAL_D[topic] : r.d.party == REP ? IDEAL_R[topic] : (IDEAL_D[topic] + IDEAL_R[topic]) / 2;
                s += k.cred[topic] * 40 - Math.abs(k.plat[topic] - med) * 6;
                if (tac[i] == 1) { r.attacks[i]++; if (r.attacks[i] > 2) s -= 6 * (r.attacks[i] - 2); }
                if (tac[i] == 4) { r.zingers[i]++; if (k.prep < 20) s -= 15; }
                // rock-paper-scissors among the main rivals
                for (int j = 0; j < n; j++) {
                    if (j == i) continue;
                    if (tac[i] == 1 && tac[j] == 0) s += 4;
                    if (tac[i] == 0 && tac[j] == 2) s += 4;
                    if (tac[i] == 2 && tac[j] == 1) s += 4;
                    if (tac[i] == 3 && tac[j] == 1) s += 3;
                    if (tac[i] == 5 && tac[j] == 1) s += 3;
                }
                double lean = HOST_LEAN[r.d.host];
                if (r.d.party < 0) s -= (k.party == DEM ? Math.max(0, lean) : Math.max(0, -lean)) * 8;
                if (k.stamina < 40) s -= 5;
                seg[i] = s;
            }
            // attacks that land hurt their targets a little
            for (int i = 0; i < n; i++) {
                if (Double.isNaN(seg[i]) || target[i] < 0 || target[i] >= n || Double.isNaN(seg[target[i]])) continue;
                if (tac[i] == 1) seg[target[i]] -= 2.5;
            }
            double mean = 0;
            int cnt = 0;
            for (double v : seg) if (!Double.isNaN(v)) { mean += v; cnt++; }
            mean /= Math.max(1, cnt);
            double[] dialRow = new double[n];
            for (int i = 0; i < n; i++) {
                if (Double.isNaN(seg[i])) { dialRow[i] = 0; continue; }
                double delta = seg[i] - mean;
                r.score[i] += delta;
                r.last[i] = delta;
                r.lastTactic[i] = tac[i];
                dialRow[i] = delta;
                r.lines.add(describe(r, i, tac[i], delta, topic));
            }
            r.dial.add(dialRow);
            // a rival goes after the player: the player gets to answer before the next question
            r.rebutBy = -1;
            if (r.myIdx >= 0) for (int i = 0; i < n; i++) {
                if (i == r.myIdx || Double.isNaN(seg[i]) || target[i] != r.myIdx || tac[i] != 1 && tac[i] != 4) continue;
                r.rebutBy = i;
                r.rebutAttack = Digital.bestAttack(g, r.parts.get(r.myIdx));
                r.rebutPower = seg[i] - mean;
                r.lines.add(who(r, i).last + " turns to " + who(r, r.myIdx).last + ": " + ATTACK_PHRASE[r.rebutAttack] + ". The moderator gives " + who(r, r.myIdx).last + " thirty seconds to respond.");
                break;
            }
            r.seg++;
            if (r.seg >= r.nSeg && r.rebutBy < 0) finish(g, r);
        }

        /** The computer picks whom to attack: usually the front-runner, sometimes whoever is rising (often the player). */
        static int aiTarget(Game g, DebateRun r, int i) {
            List<Integer> riv = rivals(g, r, i);
            if (riv.isEmpty()) return -1;
            if (r.myIdx >= 0 && r.myIdx != i && g.rng.nextDouble() < (r.parts.size() == 2 ? 1 : 0.35 + poll(g, r.parts.get(r.myIdx)))) return r.myIdx;
            return riv.get(g.rng.nextDouble() < 0.7 ? 0 : g.rng.nextInt(riv.size()));
        }

        /** The response the candidate's own instincts would choose. */
        static int aiRebut(DebateRun r) {
            int me = r.myIdx;
            double[] v = {0.5 * att(r, me, A_DEB) + 0.5 * att(r, me, A_CHA), 0.5 * att(r, me, A_INT) + 0.5 * att(r, me, A_EXE), 0.6 * att(r, me, A_CHA) + 0.4 * att(r, me, A_AUT), att(r, me, A_DIS) - 5};
            int best = 0;
            for (int i = 1; i < 4; i++) if (v[i] > v[best]) best = i;
            return best;
        }

        /** The player answers an attack. */
        static void rebut(Game g, DebateRun r, int opt) {
            int me = r.myIdx, by = r.rebutBy;
            if (me < 0 || by < 0) return;
            Camp k = r.parts.get(me);
            double sk, sd;
            switch (opt) {
                case 0: sk = 0.5 * att(r, me, A_DEB) + 0.5 * att(r, me, A_CHA); sd = 14; break;
                case 1: sk = 0.5 * att(r, me, A_INT) + 0.3 * att(r, me, A_EXE) + 0.2 * att(r, me, A_DIS); sd = 7; break;
                case 2: sk = 0.6 * att(r, me, A_CHA) + 0.4 * att(r, me, A_AUT); sd = 10; break;
                default: sk = att(r, me, A_DIS); sd = 4; break;
            }
            double fit = Message.attackFit(g, k, r.rebutAttack);
            double delta = (sk - 70) * 0.35 + Math.min(40, k.prep) * 0.1 + g.rng.nextGaussian() * sd - r.rebutPower * 0.5 - fit * 4 + (opt == 2 && r.rebutPower < 2 ? 3 : 0);
            if (opt == 3) delta = delta * 0.45 + 1;
            r.score[me] += delta * 0.6;
            r.score[by] -= delta * 0.4;
            double[] row = new double[r.parts.size()];
            row[me] = delta * 0.6; row[by] = -delta * 0.4;
            r.dial.add(row);
            String nm = who(r, me).last;
            String res = delta > 8 ? "the best moment of the night - the crowd erupts" : delta > 3 ? "a strong comeback" : delta > -3 ? "a draw - both camps claim the exchange" : delta > -8 ? "an uncomfortable moment" : "a moment that will be replayed for days - and not kindly";
            String how = opt == 0 ? " hits back hard - " : opt == 1 ? " sets the record straight - " : opt == 2 ? " laughs it off - " : " ignores the bait and pivots - ";
            r.lines.add(nm + how + res + ".");
            if (opt == 0) r.attacks[me]++;
            r.rebutBy = -1;
            if (r.seg >= r.nSeg) finish(g, r);
        }

        static String describe(DebateRun r, int i, int t, double delta, int topic) {
            String name = who(r, i).last;
            String q = delta > 8 ? "a standout moment" : delta > 3 ? "a strong answer" : delta > -3 ? "a solid but forgettable answer" : delta > -8 ? "a shaky answer" : "a rough moment";
            switch (t) {
                case 0: return name + " goes deep on " + ISSUE[topic].toLowerCase() + " policy - " + q + ".";
                case 1: return name + " goes on the attack - " + (delta > 3 ? "and lands it" : delta < -3 ? "but it falls flat with the audience" : "a sharp exchange") + ".";
                case 2: return name + " pivots to the campaign's core message - " + q + ".";
                case 3: return name + " answers with a personal story - " + q + ".";
                case 4: return name + " tries a prepared one-liner - " + (delta > 6 ? "the line of the night" : delta < -6 ? "it lands with a thud" : "a ripple of laughter") + ".";
                default: return name + " defends the record on " + ISSUE[topic].toLowerCase() + " - " + q + ".";
            }
        }

        static void finish(Game g, DebateRun r) {
            if (r.over) return;
            r.over = true;
            int n = r.parts.size();
            r.d.done = true;
            r.d.score = new double[g.camps.size()];
            int best = 0;
            for (int i = 0; i < n; i++) { r.d.score[r.parts.get(i).idx] = r.score[i]; if (r.score[i] > r.score[best]) best = i; }
            r.lines.add("");
            for (int i = 0; i < n; i++) {
                Camp k = r.parts.get(i);
                double rel = r.score[i] / Math.max(1, r.nSeg);
                double eff = clamp(rel / 6.0, -1, 1);
                double donors, dollars;
                if (r.d.party < 0) {
                    double w = r.d.vp ? 0.35 : 1.0;
                    k.favor += 0.025 * eff * w;
                    k.enthusiasm += 0.02 * eff * w;
                    donors = 300000 * Math.max(0, eff) * w;
                    dollars = donors * 32 * (0.5 + k.a(A_SMD) / 100.0);
                } else {
                    k.momentum += 0.25 * eff;
                    k.favor += 0.01 * eff;
                    NameId.learn(k, 0.05 * (r.parts.size() <= 4 ? 1.3 : 1));
                    donors = 200000 * Math.max(0, eff);
                    dollars = donors * 22 * (0.5 + k.a(A_SMD) / 100.0);
                }
                // small-dollar surge after a strong night
                if (donors > 1000) {
                    k.smallList += donors;
                    Week.raise(k, dollars, RS_SMALL);
                    k.raisedHist[Math.min(109, g.week)] += dollars;
                    if (k.human) g.log(NK_GOOD, "DEBATE BUMP: " + compact(donors) + " new small donors give " + money(dollars) + " in the 48 hours after the debate.");
                }
                if (r.zingers[i] > 0 && r.last[i] > 6) Week.viral(g, k, "a debate zinger");
                if (!r.d.vp) k.prep *= 0.6;
            }
            if (n == 2 || r.d.party < 0) {
                double diff = r.score[0] - r.score[n > 1 ? 1 : 0];
                double won = 0.5 + 0.23 * Math.tanh(diff / 28.0);
                r.headline = "Snap poll: " + who(r, 0).last + " " + Math.round(won * 100) + "%, " + who(r, 1).last + " " + Math.round((1 - won) * 100) + "% say their candidate won.";
            } else r.headline = "Pundits' verdict: " + who(r, best).last + " won the night.";
            r.lines.add(r.headline);
            String title = r.d.vp ? "VP DEBATE" : r.d.party < 0 ? "PRESIDENTIAL DEBATE" : (r.d.party == DEM ? "DEMOCRATIC" : "REPUBLICAN") + " DEBATE";
            r.d.summary = title + " (" + HOSTS[r.d.host] + "): " + r.headline;
            g.log(NK_BIG, r.d.summary);
            g.dirty = true;
        }
    }

    // =====================================================================
    //  17.  COMPUTER CAMPAIGNS AND STAFF AUTOMATION
    // =====================================================================
    static final class AI {
        static double sigma = 5.0;

        /** How much each state is worth to a campaign right now. */
        static double[] stateValue(Game g, Camp k) {
            double[] v = new double[NS];
            boolean general = g.phase >= PH_CONVENTION && k.nominee || k.party >= GRN;
            if (k.party == IND) {
                // where can the independent win - or at least finish strongly? (plus ballot drives still open)
                double[][] t = Polls.truth(g);
                Ballot.init();
                for (int s = 0; s < NS; s++) {
                    double tot = Math.max(1, t[s][0] + t[s][1] + t[s][2]);
                    double i = t[s][4] / tot * 100, top = Math.max(t[s][0], t[s][1]) / tot * 100;
                    double gap = i - top;
                    double ev = STATES[s].ev;
                    v[s] = ev * Math.exp(-(gap * gap) / (2 * 9 * 9)) + 0.08 * ev * i / 20.0;
                    if (k.onBallot != null && !k.onBallot[s]) v[s] *= g.week <= Ballot.deadline[s] ? 0.6 : 0.05;
                    if (STATES[s].ab.equals("NE") || STATES[s].ab.equals("ME")) v[s] += 1.0;
                }
                return v;
            }
            if (general) {
                double nat = Polls.trueMargin(g, -1);
                double[][] t = Polls.truth(g);
                for (int s = 0; s < NS; s++) {
                    double m = Vote.margin(t[s]);
                    double ev = STATES[s].ev;
                    double close = Math.exp(-(m * m) / (2 * sigma * sigma));
                    v[s] = ev * close + 0.15 * ev * Math.exp(-Math.abs(m - nat) / 12);
                    if (STATES[s].ab.equals("NE")) v[s] += 2.5 * Math.exp(-Math.pow(Vote.cdMargin(3, m), 2) / (2 * sigma * sigma));
                    if (STATES[s].ab.equals("ME")) v[s] += 1.5 * Math.exp(-Math.pow(Vote.cdMargin(1, m), 2) / (2 * sigma * sigma));
                }
            } else {
                int order = 0;
                for (Contest c : g.contests) {
                    if (c.party != k.party || c.done) { if (c.party == k.party) order++; continue; }
                    int away = c.week - g.week;
                    if (away < 0) continue;
                    double early = order < 4 ? 3.0 : order < 6 ? 1.8 : 1.0;
                    v[c.state] += c.delegates * early * Math.exp(-away / 6.0);
                    order++;
                }
            }
            return v;
        }

        static int[] topStates(double[] v, int n) {
            Integer[] idx = new Integer[NS];
            for (int i = 0; i < NS; i++) idx[i] = i;
            final double[] vv = v;
            Arrays.sort(idx, new Comparator<Integer>() { public int compare(Integer a, Integer b) { return Double.compare(vv[b], vv[a]); } });
            int[] o = new int[Math.min(n, NS)];
            for (int i = 0; i < o.length; i++) o[i] = idx[i];
            return o;
        }

        static boolean debateSoon(Game g, Camp k) {
            for (Debate d : g.debates) {
                if (d.done || d.week < g.week || d.week > g.week + 1) continue;
                if (d.party < 0 && k.nominee) return true;
                if (d.party == k.party && k.announced) return true;
            }
            return false;
        }

        static void decide(Game g, Camp k) {
            double burn = Week.hqWeek(g, k);
            k.hq = k.cash < burn * 3 ? 0 : k.cash > burn * 40 && (k.nominee || k.a(A_BIG) > 80) ? 2 : 1;
            schedule(g, k);
            if (Ballot.running(k)) Ballot.aiPlan(g, k);      // ballot access gets the first call on the money
            budget(g, k);
            message(g, k);
        }

        static void schedule(Game g, Camp k) {
            double[] v = stateValue(g, k);
            int[] top = topStates(v, 6);
            Arrays.fill(k.act, ACT_NONE);
            Arrays.fill(k.loc, -1);
            boolean general = k.nominee && g.phase >= PH_CONVENTION || k.party == IND;
            double burn = Week.hqWeek(g, k) * 4;
            boolean poor = k.cash < burn && k.c.selfMax - k.selfFunded < 5e6;
            int prep = debateSoon(g, k) ? (general ? 6 : 4) : 0;
            int money = poor ? 7 : general ? 3 : g.phase == PH_INVISIBLE ? 6 : 4;
            if (!k.announced && g.phase == PH_INVISIBLE) money = 4;
            int rest = k.stamina < 40 ? 4 : k.stamina < 60 ? 2 : 1;
            int media = g.phase != PH_INVISIBLE ? 2 : k.announced ? 1 : g.week % 2;
            boolean[] booked = new boolean[SHOWS.length];
            int slot = 0;
            // day by day: morning, afternoon, evening
            int ti = 0;
            for (int day = 0; day < 7; day++) {
                int st = top.length == 0 ? Math.max(0, k.c.home) : top[ti % top.length];
                if (v[st] <= 0) st = Math.max(0, k.c.home);
                ti += g.rng.nextDouble() < 0.6 ? 1 : 0;
                for (int s3 = 0; s3 < 3; s3++) {
                    slot = day * 3 + s3;
                    int a;
                    if (prep > 0 && s3 == 0) { a = ACT_PREP; prep--; }
                    else if (rest > 0 && (day == 6 || day == 3) && s3 == 0) { a = ACT_REST; rest--; }
                    else if (money > 0 && s3 == 2) { a = g.rng.nextBoolean() ? ACT_CALLTIME : ACT_FUNDRAISER; money--; }
                    else if (media > 0 && s3 == 1 && day % 3 == 1) { a = g.rng.nextDouble() < 0.5 + (k.a(A_POD) - 60) / 100.0 ? ACT_PODCAST : ACT_TV; media--; }
                    else a = pickEvent(g, k, st, s3);
                    k.act[slot] = a;
                    if (a == ACT_FUNDRAISER) k.loc[slot] = donorHub(g, k, st);
                    else k.loc[slot] = ACT_LOCAL[a] ? st : -1;
                    k.param[slot] = a == ACT_PODCAST ? Podcasts.aiPick(g, k, booked) : g.rng.nextInt(5);
                }
            }
            if (!k.announced && g.phase == PH_INVISIBLE) for (int i = 0; i < NSLOT; i++) if (k.act[i] == ACT_RALLY) k.act[i] = ACT_DINNER;
        }

        static int pickEvent(Game g, Camp k, int s, int s3) {
            boolean early = Week.primaryEarly(g, k, s);
            double r = g.rng.nextDouble();
            if (early) return r < 0.4 ? ACT_RETAIL : r < 0.7 ? ACT_TOWNHALL : ACT_RALLY;
            if (s3 == 2) return r < 0.6 ? ACT_RALLY : ACT_TOWNHALL;
            if (r < 0.35) return ACT_RALLY;
            if (r < 0.55) return ACT_TOWNHALL;
            if (r < 0.65) return ACT_UNION;
            if (r < 0.75) return ACT_COMMUNITY;
            if (r < 0.82) return ACT_CHURCH;
            if (r < 0.90) return ACT_CAMPUS;
            return ACT_RETAIL;
        }

        static int donorHub(Game g, Camp k, int near) {
            String[] hubs = {"NY", "CA", "TX", "FL", "IL", "MA", "DC", "CT", "WA", "GA"};
            if (Week.DONOR_HUBS.contains(STATES[near].ab)) return near;
            return stateIdx(hubs[g.rng.nextInt(hubs.length)]);
        }

        /** Weekly money: spend a sustainable share of cash where it matters. */
        static void budget(Game g, Camp k) {
            boolean general = k.nominee && g.phase >= PH_CONVENTION || k.party == IND;
            boolean indEarly = k.party == IND && g.phase < PH_GENERAL && g.set.mode == MODE_FULL;    // an independent before Labor Day
            int left = general ? Math.max(1, ELECTION_WEEK - g.week) : Math.max(2, nextBigContest(g, k) - g.week + 2);
            if (!k.human) {      // the player's own self-funding is never touched by staff
                if (k.c.selfMax > 0 && k.cash < 20e6 && k.selfFunded < k.c.selfMax) k.selfFundW = Math.min(k.c.selfMax - k.selfFunded, general ? 25e6 : 6e6);
                else k.selfFundW = 0;
            }
            double reserve = Week.hqWeek(g, k) * 3;
            if (Ballot.running(k)) { double pet = 0; for (double p : k.petitionW) pet += p; reserve += pet * 4; }
            double budget = Math.max(0, (k.cash - reserve)) / Math.max(1, left) * (general ? 1.25 : 1.0);
            if (indEarly) budget *= 0.5;                    // save the war chest for the fall
            if (!k.announced) budget *= 0.2;
            if (g.phase == PH_INVISIBLE && g.week < 40) budget *= 0.35;
            double[] v = stateValue(g, k);
            double vt = 0;
            for (double x : v) vt += x;
            Arrays.fill(k.tvGrp, 0);
            Arrays.fill(k.digital, 0);
            if (vt <= 0 || budget < 50000) return;
            double tvB = budget * (general ? 0.55 : 0.45), digB = budget * 0.2, fieldB = budget * (general ? 0.2 : 0.3);
            // TV: put points where value per dollar is highest
            double[] mv = new double[Elect.NM];
            double mt = 0;
            for (int m = 0; m < Elect.NM; m++) {
                double val = 0;
                for (int s : Elect.marketStates[m]) val += v[s] / vt * Elect.dmaShare[m][s];
                double cost = Week.cpp(g, m, true);
                mv[m] = val > 0.002 ? Math.pow(val, 1.4) / Math.pow(cost, 0.3) : 0;
                mt += mv[m];
            }
            if (mt > 0) for (int m = 0; m < Elect.NM; m++) {
                if (mv[m] <= 0) continue;
                double dollars = tvB * mv[m] / mt;
                double grp = dollars / Week.cpp(g, m, true);
                if (grp < 60) continue;
                k.tvGrp[m] = Math.min(1500, grp);
                k.tvTone[m] = k.aggression > 0.6 && general ? 2 : k.aggression > 0.4 ? 1 : 0;
            }
            // field: a payroll proportional to value, within realistic staffing limits
            double maxTot = indEarly ? 300 + 300 * k.a(A_VOL) / 100 : general ? 2200 + 1200 * k.a(A_VOL) / 100 : g.phase == PH_PRIMARY ? 700 : 300;
            double[] target = new double[NS];
            double tsum = 0;
            for (int s = 0; s < NS; s++) {
                k.digital[s] = v[s] > 0 ? digB * v[s] / vt : 0;
                if (k.digital[s] < 5000) k.digital[s] = 0;
                double perOrg = Week.ORGANIZER_WEEK * Week.stateCostIndex(s) + Week.officeWeek(s) / 7;
                target[s] = v[s] > 0 ? fieldB * v[s] / vt / perOrg : 0;
                target[s] = Math.min(target[s], Week.sumCvap(s) / 18000.0 * (general ? 1 : 1.6));
                if (target[s] < 3) target[s] = 0;
                tsum += target[s];
            }
            double scale = tsum > maxTot ? maxTot / tsum : 1;
            for (int s = 0; s < NS; s++) {
                double t = target[s] * scale, cur = k.organizers[s];
                if (t > cur + 2) k.organizerOrder[s] = (int) Math.min(40, Math.round((t - cur) * 0.5));
                else if (cur > t * 1.3 + 2) k.organizerOrder[s] = -(int) Math.ceil((cur - t) * 0.4);
                double after = cur + k.organizerOrder[s];
                double offTarget = Math.ceil(after / 7.0);
                if (offTarget > k.offices[s]) k.officeOrder[s] = (int) Math.min(6, offTarget - k.offices[s]);
                else if (k.offices[s] > Math.ceil(after / 5.0) + 1) k.officeOrder[s] = -(int) Math.max(1, (k.offices[s] - Math.ceil(after / 5.0)) / 2);
            }
            Digital.aiPlan(g, k);
            if (general && g.week >= 98) {
                k.nationalGrp[1] = k.a(A_BIG) > 70 ? 120 : 0;
                for (int s = 0; s < NS; s++) k.redBox[s] = v[s] > vt * 0.08;
            }
        }

        static int nextBigContest(Game g, Camp k) {
            if (k.party == IND) return ELECTION_WEEK;
            for (Contest c : g.contests) if (c.party == k.party && !c.done && c.week >= g.week) return c.week;
            return g.week + 10;
        }

        static void message(Game g, Camp k) {
            double best = -1;
            int bf = k.frame;
            for (int f = 0; f < FRAME.length; f++) {
                double v = Message.frameFit(g, k, f) + (f == k.frame ? 0.15 : 0) + g.rng.nextDouble() * 0.1;
                if (v > best) { best = v; bf = f; }
            }
            k.frame = bf;
            Camp t = Message.attackTarget(g, k);
            k.attack = 0;
            if (t != null && k.aggression > 0.3) {
                double ba = 0.35;
                for (int a = 1; a < ATTACK.length; a++) {
                    double v = Message.attackFit(g, t, a) + g.rng.nextDouble() * 0.1;
                    if (v > ba) { ba = v; k.attack = a; }
                }
            }
            // issue focus: the most salient issues the campaign is credible on (one main, two secondary)
            final double[] iv = new double[NI];
            Integer[] order = new Integer[NI];
            for (int i = 0; i < NI; i++) {
                order[i] = i;
                double dist = Math.abs(k.plat[i] - (k.party == DEM ? IDEAL_D[i] : k.party == REP ? IDEAL_R[i] : (IDEAL_D[i] + IDEAL_R[i]) / 2));
                iv[i] = ISSUE_SAL[i] * g.salience[i] * (1 + k.cred[i] * 4) * (1.2 - Math.min(1, dist)) * (1 + 0.05 * Math.min(6, k.focusWeeks[i]));
            }
            Arrays.sort(order, (p, q) -> Double.compare(iv[q], iv[p]));
            k.focusIssue = order[0];
            k.focus2[0] = order[1];
            k.focus2[1] = order[2];
        }

        /** The computer's answer to an event, by party and faction. */
        static int choose(Game g, Camp k, int ev, int n) {
            boolean dem = k.party == DEM;
            int f = k.c.faction;
            boolean left = f == F_DSOC || f == F_PROG;
            boolean mod = f == F_MOD || f == F_OUTD || f == F_GOV || f == F_ABUN;
            switch (ev) {
                case EV_AI: return dem ? 0 : (f == F_TECHR ? 1 : f == F_NATCON ? 0 : 2);
                case EV_GRID: return dem ? (left ? 0 : 1) : (f == F_TECHR || f == F_HAWK ? 2 : 1);
                case EV_SHOOTING: return dem ? (mod ? 0 : 0) : 1;
                case EV_CAMPUS: return dem ? (left ? 1 : 2) : (f == F_RESTR ? 2 : 0);
                case EV_BORDER: return dem ? (left ? 1 : 2) : 0;
                case EV_SCOTUS: return dem ? 0 : 1;
                case EV_HURRICANE: return 0;
                case EV_CHINA: return dem ? (left ? 1 : 0) : (f == F_RESTR ? 1 : 0);
                case EV_ADMIN: return dem ? 0 : (k.a(A_BAS) > 75 && g.phase <= PH_PRIMARY ? 2 : mod ? 1 : 2);
                case EV_WAR: return left ? 1 : 0;
                case EV_GAFFE: return k.a(A_AUT) > 70 && k.a(A_BAS) > 75 ? 1 : 0;
                case EV_SCANDAL: return k.a(A_DIS) > 70 ? 0 : g.rng.nextInt(3);
                default: return 0;
            }
        }
    }

    /** Lets the player hand parts of the campaign to staff. */
    static final class Staff {
        static final String[] AREA = {"Schedule", "Ads", "Field", "Polling", "Message"};

        static void automate(Game g, Camp k) {
            boolean[] on = g.set.staff;
            if (on[0]) AI.schedule(g, k);
            if (on[1] || on[2]) {
                Digital.ensure(k);
                double[] tv = k.tvGrp.clone(), dg = k.digital.clone(), mix = k.digMix.clone();
                int[] msg = k.digMsg.clone();
                int[] oo = k.officeOrder.clone(), or = k.organizerOrder.clone();
                double[] nat = k.nationalGrp.clone();
                boolean[] rb = k.redBox.clone();
                if (on[2] && Ballot.running(k)) Ballot.aiPlan(g, k);
                AI.budget(g, k);
                if (!on[1]) { k.tvGrp = tv; k.digital = dg; k.digMix = mix; k.digMsg = msg; k.nationalGrp = nat; k.redBox = rb; }
                if (!on[2]) { k.officeOrder = oo; k.organizerOrder = or; }
            }
            if (on[3]) pollingPlan(g, k);
            if (on[4]) AI.message(g, k);
        }

        static final String[] POLL_LEVEL = {"Light", "Standard", "Heavy"};

        /**
         * The pollster's plan: keep fresh numbers where the race will be decided. Upcoming contests in the primaries,
         * the battlegrounds in the general, a national poll every few weeks; spend scales with the level and with cash.
         */
        static void pollingPlan(Game g, Camp k) {
            int lvl = clampi(g.set.staffPolling, 0, 2);
            double burn = Week.hqWeek(g, k);
            if (k.cash < burn * 2 + 1e6) return;
            boolean general = g.phase >= PH_CONVENTION || k.party == IND || g.set.mode == MODE_GENERAL;
            int every = lvl == 2 ? 1 : lvl == 1 ? 2 : 4;                 // weeks between polls in each target state
            int states = general ? (lvl == 2 ? 12 : lvl == 1 ? 9 : 5) : (lvl == 2 ? 6 : lvl == 1 ? 4 : 2);
            int n = lvl == 2 ? 1200 : lvl == 1 ? 800 : 600, method = lvl == 2 ? 2 : 1;
            if (g.phase == PH_INVISIBLE && g.week < 30) { states = Math.min(states, 2); every = Math.max(every, 4); }
            double[] v = AI.stateValue(g, k);
            int[] top = AI.topStates(v, states);
            double budget = Math.max(0, k.cash - burn * 2) * (lvl == 2 ? 0.06 : lvl == 1 ? 0.035 : 0.02);
            int bought = 0;
            for (int s : top) {
                if (v[s] <= 0) continue;
                InternalPoll last = Polls.latest(g, k.idx, s);
                if (last != null && g.week - last.week < every) continue;
                double cost = n * POLL_COST_PER[method] + 6000;
                if (cost > budget) break;
                if (Polls.buy(g, k, s, n, method) != null) { budget -= cost; bought++; }
            }
            InternalPoll nat = Polls.latest(g, k.idx, -1);
            if ((nat == null || g.week - nat.week >= every * 2) && budget > n * POLL_COST_PER[method] * 1.3 + 6000) Polls.buy(g, k, -1, n, method);
        }

        /** Fills empty slots of the player's week with sensible defaults (the 'auto-fill' button). */
        static void fill(Game g, Camp k) {
            int[] act = k.act.clone(), loc = k.loc.clone(), par = k.param.clone();
            AI.schedule(g, k);
            for (int i = 0; i < NSLOT; i++) if (act[i] != ACT_NONE) { k.act[i] = act[i]; k.loc[i] = loc[i]; k.param[i] = par[i]; }
        }
    }

    // =====================================================================
    //  18.  PLAYER ACTIONS THAT ARE NOT PART OF THE WEEKLY ORDERS
    //       (policy promises, opposition research, running-mate vetting, self-funding)
    // =====================================================================
    static final class Campaign {
        static final double OPPO_COST = 450000, VET_COST = 150000;

        /** Would this promise contradict one already made (same issue, opposite direction)? */
        static int conflict(Camp k, int p) {
            Promise a = PROMISES[p];
            for (int q : k.promises) {
                Promise b = PROMISES[q];
                if (b.issue == a.issue && Math.abs(b.target - a.target) > 0.9) return q;
            }
            return -1;
        }

        /** How far the promise sits from the candidate's ideology (0 = squarely in it, 1+ = a reversal). */
        static double offIdeology(Camp k, int p) {
            Promise a = PROMISES[p];
            return Math.abs(a.target - FACTIONS[k.c.faction].pos[a.issue]);
        }

        /** Makes a promise. Moves the platform, builds credibility on the issue, and draws group reactions. */
        static void promise(Game g, Camp k, int p) {
            if (k.promises.contains(p)) return;
            Promise pr = PROMISES[p];
            int clash = conflict(k, p);
            double before = k.plat[pr.issue];
            k.promises.add(p);
            k.plat[pr.issue] = lerp(k.plat[pr.issue], pr.target, 0.6);
            k.cred[pr.issue] += 0.035;
            k.favor += pr.valence;
            for (int i = 0; i < pr.g.length; i++) k.imageG[pr.g[i]] += pr.v[i];
            // donors: negative values mean big donors close their wallets
            if (pr.donor < 0) k.donorsTapped += -pr.donor * 80e6;
            else k.donorsTapped = Math.max(0, k.donorsTapped - pr.donor * 40e6);
            // reversals and off-ideology promises cost credibility (authenticity blunts it)
            double off = offIdeology(k, p);
            if (off > 0.6) k.flipflop += (off - 0.6) * 0.6;
            if (Math.abs(before - pr.target) > 0.9) k.flipflop += 0.12;
            if (clash >= 0) { k.flipflop += 0.35; g.log(k.human ? NK_BAD : NK_INFO, k.last() + "'s new promise contradicts an earlier one: \"" + PROMISES[clash].name + "\"."); }
            g.newsSal[pr.issue] += 0.08;
            g.log(k.human ? NK_GOOD : NK_INFO, k.last() + " promises: " + pr.name + ".");
            g.dirty = true;
        }

        /** A short forecast of how a promise would land, for the promise list. */
        static String preview(Game g, Camp k, int p) {
            Promise pr = PROMISES[p];
            StringBuilder b = new StringBuilder(pr.desc);
            b.append("\nIssue: ").append(ISSUE[pr.issue]).append(" - moves you toward \"").append(ISSUE_POLES[pr.issue][pr.target < 0 ? 0 : 1]).append("\".");
            if (pr.g.length > 0) {
                b.append("\nReactions: ");
                for (int i = 0; i < pr.g.length; i++) b.append(i > 0 ? ", " : "").append(GROUP[pr.g[i]]).append(pr.v[i] > 0 ? " +" : " -");
            }
            if (pr.donor <= -0.08) b.append("\nBig donors will close their wallets.");
            else if (pr.donor >= 0.05) b.append("\nBig donors like it.");
            double off = offIdeology(k, p);
            if (off > 0.6) b.append("\nOff-message for a ").append(FACTIONS[k.c.faction].name).append(": activists cool and flip-flop attacks get easier - unless swing voters reward the move to the middle.");
            int c = conflict(k, p);
            if (c >= 0) b.append("\nContradicts your promise: ").append(PROMISES[c].name).append(".");
            return b.toString();
        }

        /** Opposition research on a rival: reveals ratings and raises the chance a scandal surfaces. */
        static boolean research(Game g, Camp k, Camp t) {
            if (k.cash < OPPO_COST || t == null) return false;
            Week.pay(g, k, OPPO_COST, -1);
            List<Integer> hidden = new ArrayList<Integer>();
            for (int a = 0; a < NA; a++) if (!t.known[a]) hidden.add(a);
            Collections.shuffle(hidden, g.rng);
            for (int i = 0; i < Math.min(6, hidden.size()); i++) t.known[hidden.get(i)] = true;
            t.oppoOnOpponent += 0.4;
            k.oppoFiles++;
            g.log(NK_INFO, "Opposition research on " + t.name() + " delivered: " + Math.min(6, hidden.size()) + " ratings confirmed. A file is ready to leak.");
            return true;
        }

        /** Leak the file. */
        static void leak(Game g, Camp k, Camp t) {
            if (k.oppoFiles <= 0 || t == null) return;
            k.oppoFiles--;
            Events.dropOppo(g, k, t);
        }

        /** Progressive voters dislike candidates who buy the race: a minor penalty that grows with personal spending. */
        static double selfFundPenalty(Camp k, int s, int c) {
            if (k.selfFunded < 20e6) return 0;
            double[] id = Elect.ideal[s][c];
            double prog = clamp((-(id[I_COL] + id[I_HEALTH]) / 2 - 0.35) / 0.45, 0, 1);
            return 0.035 * Math.log1p(k.selfFunded / 50e6) * prog;
        }

        /** A one-time check from the candidate's own fortune. */
        static double lumpSum(Game g, Camp k, double amt) {
            double left = k.c.selfMax - k.selfFunded;
            amt = Math.min(amt, left);
            if (amt <= 0) return 0;
            k.selfFunded += amt;
            Week.raise(k, amt, RS_SELF);
            k.raisedHist[Math.min(109, g.week)] += amt;
            g.log(k.human ? NK_INFO : NK_INFO, k.last() + " writes the campaign a personal check for " + money(amt) + ".");
            if (amt >= 50e6) k.attackStick[2] += 0.1;
            return amt;
        }

        /** Vetting a possible running mate reveals scandal risk. */
        static boolean vet(Game g, Camp k, Cand c) {
            if (k.cash < VET_COST) return false;
            Week.pay(g, k, VET_COST, -1);
            if (!k.vetted.contains(c.id)) k.vetted.add(c.id);
            return true;
        }

        /** Rival ratings as the player sees them: exact if known, otherwise a stable fuzzy estimate. */
        static int seen(Game g, Camp viewer, Camp t, int a) {
            if (t == viewer || t.known[a] || g.set.godMode) return t.attr[a];
            int h = (int) ((t.c.id.hashCode() * 31L + a * 977L + g.set.seed * 13L) % 17);
            return clampi(t.attr[a] + Math.abs(h) - 8, 0, 100);
        }

        static boolean exact(Game g, Camp viewer, Camp t, int a) { return t == viewer || t.known[a] || g.set.godMode; }

        /** Projected effect of a running mate on the ticket, in points of national margin and in their home state. */
        static double[] vpEffect(Game g, Camp k, Cand vp) {
            double nat = 0, home = 0, w = 0, hw = 0;
            for (int s = 0; s < NS; s++) for (int c = 0; c < NCELL; c += 2) {
                double n = Elect.cvap[s][c] * Elect.turnB[s][c];
                double e = Vote.vpTerm(g, vp, s, c) * Elect.elast[s][c];
                nat += n * e; w += n;
                if (s == vp.home) { home += n * e; hw += n; }
            }
            double sign = 1;
            return new double[]{sign * nat / w * 50, hw > 0 ? sign * home / hw * 50 : 0};
        }
    }

    // =====================================================================
    //  19.  NAME RECOGNITION  -  by state; national = population-weighted average
    // =====================================================================
    static final class NameId {
        static double[] popW;

        static double[] popW() {
            if (popW == null) {
                popW = new double[NS];
                double t = 0;
                for (int s = 0; s < NS; s++) { popW[s] = Week.sumCvap(s); t += popW[s]; }
                for (int s = 0; s < NS; s++) popW[s] /= t;
            }
            return popW;
        }

        /** Starting name ID: much higher at home, a little higher in the home region; the national average matches the rating. */
        static void init(Camp k) {
            double base = clamp(k.c.nameId, 0.02, 0.99);
            int home = k.c.home;
            double[] w = popW();
            for (int s = 0; s < NS; s++) {
                double v = base;
                if (s == home) v = Math.max(base, 0.62 + 0.36 * base);
                else if (home >= 0 && STATES[s].region.equals(STATES[home].region)) v = base + (1 - base) * 0.06;
                k.nameIdS[s] = v;
            }
            // rescale everywhere except home so the national average equals the rating
            for (int it = 0; it < 6; it++) {
                double avg = 0;
                for (int s = 0; s < NS; s++) avg += w[s] * k.nameIdS[s];
                double f = base / Math.max(0.01, avg);
                for (int s = 0; s < NS; s++) if (s != home) k.nameIdS[s] = clamp(k.nameIdS[s] * f, 0.01, 0.99);
            }
            recompute(k);
        }

        static void recompute(Camp k) {
            double[] w = popW();
            double v = 0;
            for (int s = 0; s < NS; s++) v += w[s] * k.nameIdS[s];
            k.nameId = clamp(v, 0, 1);
        }

        /** A share of those who don't know the candidate learn who they are, everywhere. */
        static void learn(Camp k, double frac) {
            if (frac <= 0) return;
            frac = clamp(frac, 0, 1);
            for (int s = 0; s < NS; s++) k.nameIdS[s] += (1 - k.nameIdS[s]) * frac;
            recompute(k);
        }

        /** National exposure: about `pts` of the people who don't know the candidate learn who they are (diminishing near saturation). */
        static void boost(Camp k, double pts) {
            if (pts <= 0) return;
            learn(k, pts);
        }

        static void learnState(Camp k, int s, double frac) {
            if (s < 0 || frac <= 0) return;
            k.nameIdS[s] += (1 - k.nameIdS[s]) * clamp(frac, 0, 1);
        }

        /** Weekly local exposure: TV, digital, visits and doors; plus national press coverage that follows the polls. */
        static void week(Game g, Camp k) {
            double F = Week.F;
            for (int s = 0; s < NS; s++) {
                double tv = StatePanel.tvInState(k, s) + 0.75 * k.superPac[s] * 0.5;
                double adults = Math.max(1, Week.sumCvap(s));
                double dig = k.digital[s] / Digital.avgCpm(k) * 1000 / adults;
                double contacts = (k.doorsW[s] * 0.25 + k.callsW[s] * 0.05 + k.textsW[s] * 0.02) / adults;
                double vis = k.visit[s];
                double x = tv / 100.0 * 0.012 + dig * 0.02 + vis * 0.008 + contacts * 0.6;
                k.nameIdS[s] += (1 - k.nameIdS[s]) * (1 - Math.exp(-x * F));
            }
            // national coverage: the press covers whoever is winning
            double cov;
            if (g.phase >= PH_CONVENTION && (k.nominee || k.party >= GRN)) cov = k.nominee && k.party <= REP ? 0.03 : 0.006 + 0.05 * g.thirdPoll(k);
            else {
                double share = g.primaryPoll != null && k.idx < g.primaryPoll.length ? g.primaryPoll[k.idx] : 0;
                double phaseMul = g.phase == PH_PRIMARY ? 1.6 : k.announced ? 1.0 : 0.4;
                cov = (0.0035 + 0.035 * share) * phaseMul;
                if (k.party == IND) cov = 0.004 + 0.05 * g.thirdPoll(k);
            }
            learn(k, cov * F);
            recompute(k);
        }
    }

    // =====================================================================
    //  19b.  ISSUE FOCUS, CREDIBILITY AND ISSUE POLLING
    // =====================================================================
    static final class Issues {
        /** Credibility saturates: shown to the player as 0-100 of this cap. */
        static final double CRED_CAP = 0.35;

        static void build(Camp k, int i, double amt) {
            if (i < 0 || i >= NI || amt == 0) return;
            if (amt > 0) k.cred[i] += amt * Math.max(0, 1 - k.cred[i] / CRED_CAP);
            else k.cred[i] = Math.max(-0.1, k.cred[i] + amt);
        }

        static int credScore(Camp k, int i) { return (int) Math.round(clamp(k.cred[i] / CRED_CAP, -0.3, 1) * 100); }

        static boolean inFocus(Camp k, int i) { return i == k.focusIssue || i == k.focus2[0] || i == k.focus2[1]; }

        /** Talking points on the trail: events touch the main focus, and the secondary issues less. */
        static void speak(Camp k, double w) {
            build(k, k.focusIssue, 0.0013 * w);
            for (int i : k.focus2) if (i >= 0) build(k, i, 0.0006 * w);
        }

        /** Sets the main focus; the old main issue becomes a secondary one. */
        static void setMain(Camp k, int i) {
            if (i == k.focusIssue) return;
            int old = k.focusIssue;
            if (k.focus2[0] == i) k.focus2[0] = old;
            else if (k.focus2[1] == i) k.focus2[1] = old;
            k.focusIssue = i;
        }

        /** Toggles a secondary issue (at most two; the oldest drops off). */
        static void toggleSecondary(Camp k, int i) {
            if (i == k.focusIssue) return;
            if (k.focus2[0] == i) { k.focus2[0] = k.focus2[1]; k.focus2[1] = -1; return; }
            if (k.focus2[1] == i) { k.focus2[1] = -1; return; }
            if (k.focus2[0] < 0) k.focus2[0] = i;
            else if (k.focus2[1] < 0) k.focus2[1] = i;
            else { k.focus2[0] = k.focus2[1]; k.focus2[1] = i; }
        }

        /** Weekly: sticking with issues compounds credibility and pushes them up the news agenda. */
        static void week(Game g, Camp k) {
            double F = Week.F;
            int m = k.focusIssue;
            double disc = 0.6 + k.a(A_DIS) / 250.0 + k.a(A_SPE) / 400.0;
            double streak = Math.min(1.6, 0.6 + k.focusWeeks[m] * 0.08);
            build(k, m, 0.006 * disc * streak * F);
            for (int i : k.focus2) if (i >= 0) build(k, i, 0.0025 * disc * Math.min(1.4, 0.6 + k.focusWeeks[i] * 0.06) * F);
            if (k.human || k.nominee || k.party == IND) {
                double loud = k.nominee || k.party == IND ? 1.5 : 0.6;
                g.newsSal[m] += 0.012 * streak * loud * F;
                for (int i : k.focus2) if (i >= 0) g.newsSal[i] += 0.004 * loud * F;
            }
            if (g.day < 0 || g.day == 6) for (int i = 0; i < NI; i++) k.focusWeeks[i] = inFocus(k, i) ? k.focusWeeks[i] + 1 : Math.max(0, k.focusWeeks[i] - 2);
        }

        // ---------------------------------------------------------------- issue polling
        static double[] mipCache;
        static int mipKey = -999;

        /** Most-important-issue shares (sum to 1) nationally (s < 0) or in a state; optionally within a group (grp >= 0). */
        static double[] mip(Game g, int s, int grp) {
            int key = (g.week * 64 + (s + 1)) * 40 + (grp + 1);
            if (s < 0 && grp < 0 && mipCache != null && mipKey == key && !g.dirty) return mipCache;
            double[] v = new double[NI];
            double tw = 0;
            int s0 = s < 0 ? 0 : s, s1 = s < 0 ? NS : s + 1;
            double[] tmp = new double[NI];
            for (int st = s0; st < s1; st++)
                for (int c = 0; c < NCELL; c++) {
                    double n = Elect.cvap[st][c] * Elect.turnB[st][c] * (grp >= 0 ? Vote.member(grp, st, c) : 1);
                    if (n <= 0) continue;
                    double z = 0;
                    for (int i = 0; i < NI; i++) { tmp[i] = Math.pow(Elect.sal[st][c][i] * g.salience[i], 1.35); z += tmp[i]; }
                    for (int i = 0; i < NI; i++) v[i] += n * tmp[i] / z;
                    tw += n;
                }
            for (int i = 0; i < NI; i++) v[i] /= Math.max(1e-9, tw);
            if (s < 0 && grp < 0) { mipCache = v; mipKey = key; }
            return v;
        }

        /** The rival the player is measured against on the issues. */
        static Camp rival(Game g, Camp me) {
            if (me == null) return null;
            if (g.phase >= PH_CONVENTION || g.set.mode == MODE_GENERAL || me.party > REP) {
                if (me.party == DEM || me.party == REP) return g.genCand(me.party == DEM ? REP : DEM);
                Camp d = g.genCand(DEM), r = g.genCand(REP);
                if (d == null) return r;
                if (r == null) return d;
                return Polls.trueShare(g, d) >= Polls.trueShare(g, r) ? d : r;
            }
            Camp best = null;
            double[] pp = g.primaryPoll;
            for (Camp k : g.camps) if (k != me && k.active && k.party == me.party && (best == null || pp != null && pp[k.idx] > pp[best.idx])) best = k;
            return best;
        }

        /**
         * Trust to handle an issue, me vs rival, among voters in a state (or the nation) and group:
         * credibility, how close each platform is to the voter, and partisanship. Returns {me %, rival %}.
         */
        static double[] trust(Game g, Camp me, Camp op, int i, int s, int grp) {
            if (me == null || op == null) return new double[]{0, 0};
            boolean sameParty = me.party == op.party;
            Camp d = g.genCand(DEM), r = g.genCand(REP);
            double a = 0, b = 0, tw = 0;
            int s0 = s < 0 ? 0 : s, s1 = s < 0 ? NS : s + 1;
            for (int st = s0; st < s1; st++)
                for (int c = 0; c < NCELL; c += (s < 0 ? 2 : 1)) {
                    double n = Elect.cvap[st][c] * Elect.turnB[st][c] * (grp >= 0 ? Vote.member(grp, st, c) : 1);
                    if (n <= 0) continue;
                    if (sameParty) {
                        double ps = Vote.primaryWeight(me.party, st, c);
                        n = ps * (grp >= 0 ? Vote.member(grp, st, c) : 1);
                        if (n <= 0) continue;
                    }
                    double id = sameParty ? Vote.primaryIdealCached(me.party, st, c, i) : Elect.ideal[st][c][i];
                    double u = 2.6 * (me.cred[i] - op.cred[i]) + 1.6 * (Math.abs(op.plat[i] - id) - Math.abs(me.plat[i] - id));
                    if (!sameParty) {
                        double pd = Elect.baseL[st][c] + Elect.swing2026(c) + Elect.elast[st][c] * g.env / 50.0;
                        double lean = me.party == DEM ? pd : me.party == REP ? -pd : (op.party == DEM ? -pd : pd) * 0.4;
                        u += 0.8 * lean;
                    }
                    double know = clamp(Math.min(me.nameIdS[st], op.nameIdS[st]), 0.2, 1);
                    double unsure = 0.12 + 0.25 * (1 - know);
                    double p = logistic(u);
                    a += n * (1 - unsure) * p;
                    b += n * (1 - unsure) * (1 - p);
                    tw += n;
                }
            return tw <= 0 ? new double[]{0, 0} : new double[]{a / tw * 100, b / tw * 100};
        }

        /** Does a voter with this ideal point agree with the position? Same side of the debate, unless the position is far more extreme. */
        static double agree(double pos, double id) {
            if (Math.abs(pos) < 0.2) return logistic(5 * (0.4 - Math.abs(id - pos)));
            double side = id * Math.signum(pos);
            return logistic(6 * (side + 0.08) - 2.5 * Math.max(0, Math.abs(pos) - Math.max(0, side) - 0.45));
        }

        /** Share of voters (state or nation, optional group) who agree with a position; party >= 0: that party's primary voters. */
        static double support(Game g, double pos, int i, int s, int grp) { return support(g, pos, i, s, grp, -1); }

        static double support(Game g, double pos, int i, int s, int grp, int party) {
            double v = 0, tw = 0;
            int s0 = s < 0 ? 0 : s, s1 = s < 0 ? NS : s + 1;
            for (int st = s0; st < s1; st++)
                for (int c = 0; c < NCELL; c += (s < 0 ? 2 : 1)) {
                    double n = party >= 0 ? Vote.primaryWeight(party, st, c) : Elect.cvap[st][c] * Elect.turnB[st][c];
                    n *= grp >= 0 ? Vote.member(grp, st, c) : 1;
                    if (n <= 0) continue;
                    double id = party >= 0 ? Vote.primaryIdealCached(party, st, c, i) : Elect.ideal[st][c][i];
                    // within a party almost everyone is on the same side, so what matters is how close the position is
                    v += n * (party >= 0 ? logistic(4.5 * (0.35 - Math.abs(pos - id))) : agree(pos, id));
                    tw += n;
                }
            return tw <= 0 ? 0 : v / tw * 100;
        }

        /** Weekly snapshot for the trend charts. */
        static void record(Game g) {
            int w = Math.min(109, g.week);
            g.mipHist[w] = mip(g, -1, -1).clone();
            Camp me = g.me(), op = rival(g, me);
            if (me != null && op != null) {
                double[] t = new double[NI];
                for (int i = 0; i < NI; i++) { double[] tr = trust(g, me, op, i, -1, -1); t[i] = tr[0] - tr[1]; }
                g.trustHist[w] = t;
                if (g.trustRival == null) g.trustRival = new int[110];
                g.trustRival[w] = op.idx;
            }
        }
    }

    // =====================================================================
    //  19c.  THE CLOSING ARGUMENT  -  the final two weeks
    // =====================================================================
    static final int DAILY_START = 102;            // Monday 23 October 2028: one day per turn from here
    static final int EV_CLOSING = 33, EV_ENDORSE = 34, EV_DROPOUT = 35, EV_IND_BALLOT = 36;
    static final String[] CLOSING = {"Your wallet: lower costs", "Change vs. more of the same", "Character and temperament",
            "Unity: turn the page", "Freedom and rights", "Safety and security", "It's close: just vote"};
    static final String[] CLOSING_DESC = {
            "Groceries, rent, power bills: a final pitch on the issue voters rank first. Safe and broad.",
            "This election is a choice about direction. Strongest for the out-party and for outsiders when the president is unpopular.",
            "Make the race about who can be trusted with the job. Strongest against an undisciplined or scandal-hit opponent.",
            "Lower the temperature and speak to the other side. Rewards crossover appeal; cools the base a little.",
            "Abortion rights, democracy, personal liberty. Mobilises women and college graduates.",
            "Crime, the border and a dangerous world. Moves seniors, suburban parents and veterans.",
            "No persuasion - pure mobilisation. Turns out your supporters; best when the race is tied and your field program is strong."};

    static final class Closing {
        static double fit(Game g, Camp k, int c) {
            boolean outParty = k.party != REP;     // a Republican holds the White House
            Camp op = Issues.rival(g, k);
            double v = 0.6;
            switch (c) {
                case 0: v += 0.25 + 0.06 * (g.inflation - 2.5) + 0.03 * (k.cred[I_COL] / Issues.CRED_CAP * 10); break;
                case 1: v += (outParty ? 0.25 : -0.3) + (g.approval < 42 ? 0.2 : 0) + (k.party == IND ? 0.3 : 0) + (k.a(A_EST) < 40 ? 0.1 : 0); break;
                case 2: v += op == null ? 0 : (100 - op.a(A_DIS)) / 160.0 + op.scandalRevealed * 0.3 + Vote.attackDamage(op) * 3 - 0.2; break;
                case 3: v += (k.a(A_XOV) - 55) / 70.0; break;
                case 4: v += k.party == DEM ? 0.35 + 0.15 * (g.salience[I_ABORT] - 1) + 0.1 * (g.salience[I_DEMOC] - 1) : -0.4; break;
                case 5: v += k.party == REP ? 0.3 + 0.1 * (g.salience[I_CRIME] - 1) + 0.1 * (g.salience[I_IMMIG] - 1) + (g.set.scenario == SC_WAR ? 0.25 : 0) : -0.15 + (k.a(A_FPC) - 60) / 120.0; break;
                case 6: {
                    double m = Math.abs(Polls.trueMargin(g, -1));
                    double field = 0;
                    for (int s = 0; s < NS; s++) field += k.organizers[s];
                    v += (m < 3 ? 0.25 : -0.1) + Math.min(0.25, field / 8000.0);
                    break;
                }
                default: break;
            }
            return clamp(v, 0.05, 1.5);
        }

        static int aiPick(Game g, Camp k) {
            int best = 0;
            for (int c = 1; c < CLOSING.length; c++) if (fit(g, k, c) > fit(g, k, best)) best = c;
            return best;
        }

        static void choose(Game g, Camp k, int c) {
            if (k.closing == c) return;
            boolean loud = k.human || k.party <= REP || k.party == IND;
            if (!loud) { k.closing = c; return; }
            if (k.closing >= 0) {
                k.closingStr *= 0.3;
                k.closingSwitches++;
                k.flipflop += 0.03;
                g.log(k.human ? NK_BAD : NK_INFO, k.last() + " shifts to a new closing argument: \"" + CLOSING[c] + "\". Reporters call it a sign of nerves.");
            } else g.log(k.human ? NK_INFO : NK_INFO, k.last() + "'s closing argument: \"" + CLOSING[c] + "\".");
            k.closing = c;
        }

        /** Each day of the final stretch the closing argument builds and does its work. */
        static void day(Game g, Camp k) {
            if (k.closing < 0 || g.day < 0) return;
            double F = Week.F;
            double fit = fit(g, k, k.closing);
            double disc = 0.55 + k.a(A_DIS) / 220.0;
            k.closingStr = Math.min(1.5, k.closingStr + 0.63 * fit * disc * F);
            double str = k.closingStr;
            switch (k.closing) {
                case 0: k.favor += 0.009 * str * (fit - 0.35) * F; Issues.build(k, I_COL, 0.003 * F); break;
                case 1: k.favor += 0.009 * str * (fit - 0.4) * F; k.enthusiasm += 0.004 * str * F; break;
                case 2: { Camp op = Issues.rival(g, k); if (op != null) op.attackStick[10] += 0.05 * str * fit * F; k.negativity += 0.02 * F; break; }
                case 3: k.favor += 0.011 * str * (fit - 0.4) * F; k.enthusiasm -= 0.002 * F; k.imageG[21] += 0.003 * str * F; break;
                case 4: k.imageG[4] += 0.006 * str * fit * F; k.imageG[9] += 0.004 * str * fit * F; k.enthusiasm += 0.004 * str * F; break;
                case 5: k.imageG[1] += 0.005 * str * fit * F; k.imageG[21] += 0.004 * str * fit * F; k.imageG[25] += 0.004 * str * fit * F; break;
                case 6: k.enthusiasm += 0.008 * str * F; for (int s = 0; s < NS; s++) if (k.organizers[s] > 0) k.gotv[s] += 0.0035 * str * F; break;
                default: break;
            }
        }
    }

    // =====================================================================
    //  19d.  BALLOT ACCESS FOR AN INDEPENDENT
    //  Signature requirements and filing deadlines for independent presidential candidates, 2024 rules
    //  (NASS summary of state ballot-access laws, July 2024; filing deadlines as compiled on Wikipedia,
    //  "Ballot access in the 2024 United States presidential election"). Percentage rules are converted to
    //  approximate counts. The game assumes the same rules and calendar dates in 2028.
    // =====================================================================
    static final String[][] BALLOT_RULES = {
            {"AL", "5000", "08-15"}, {"AK", "3400", "08-07"}, {"AZ", "42300", "08-17"}, {"AR", "5000", "08-01"}, {"CA", "219400", "08-09"},
            {"CO", "5000", "07-11"}, {"CT", "7500", "08-07"}, {"DE", "7500", "09-03"}, {"DC", "5000", "08-07"}, {"FL", "145000", "07-15"},
            {"GA", "7500", "07-09"}, {"HI", "5000", "08-07"}, {"ID", "1000", "08-01"}, {"IL", "25000", "06-24"}, {"IN", "36900", "07-15"},
            {"IA", "3500", "08-16"}, {"KS", "5000", "08-05"}, {"KY", "5000", "09-06"}, {"LA", "5000", "08-23"}, {"ME", "4000", "08-01"},
            {"MD", "10000", "08-05"}, {"MA", "10000", "08-27"}, {"MI", "30000", "07-18"}, {"MN", "2000", "08-20"}, {"MS", "1000", "09-06"},
            {"MO", "10000", "07-29"}, {"MT", "5000", "08-14"}, {"NE", "2500", "08-01"}, {"NV", "10100", "07-05"}, {"NH", "3000", "06-14"},
            {"NJ", "800", "07-29"}, {"NM", "21000", "06-27"}, {"NY", "45000", "05-28"}, {"NC", "83200", "03-05"}, {"ND", "4000", "09-03"},
            {"OH", "5000", "09-01"}, {"OK", "34600", "07-15"}, {"OR", "23700", "08-27"}, {"PA", "5000", "08-01"}, {"RI", "1000", "09-06"},
            {"SC", "10000", "07-15"}, {"SD", "3500", "08-06"}, {"TN", "275", "08-15"}, {"TX", "113200", "05-13"}, {"UT", "1000", "06-15"},
            {"VT", "1000", "08-01"}, {"VA", "5000", "08-23"}, {"WA", "1000", "08-02"}, {"WV", "8000", "08-01"}, {"WI", "2000", "08-06"},
            {"WY", "4000", "08-26"}};
    /** Share of raw petition signatures that survive validation. */
    static final double SIG_VALID = 0.72;
    /** Cost of one raw signature from paid circulators (2024 rates were roughly $10-$20). */
    static final double SIG_COST = 13;

    static final class Ballot {
        static int[] need, deadline;

        static void init() {
            if (need != null) return;
            need = new int[NS];
            deadline = new int[NS];
            for (String[] r : BALLOT_RULES) {
                int s = stateIdx(r[0]);
                if (s < 0) continue;
                need[s] = Integer.parseInt(r[1]);
                deadline[s] = Setup.weekOfStr("2028-" + r[2]);
            }
        }

        static boolean running(Camp k) { return k != null && k.party == IND && k.onBallot != null; }

        static double valid(Camp k, int s) { return k.sigs[s] * SIG_VALID; }

        static boolean open(Game g, Camp k, int s) { init(); return !k.onBallot[s] && g.week <= deadline[s]; }

        /** Weekly: paid circulators and volunteers gather signatures; states certify or close. */
        static void week(Game g) {
            init();
            for (Camp k : g.camps) {
                if (!running(k) || !k.active) continue;
                for (int s = 0; s < NS; s++) {
                    if (k.onBallot[s]) continue;
                    if (g.week > deadline[s]) continue;
                    double paid = k.petitionW[s] * Week.F;
                    if (paid > 0 && k.cash > paid) {
                        Week.pay(g, k, paid, s);
                        k.sigs[s] += paid / SIG_COST;
                    }
                    k.sigs[s] += (k.volunteers[s] * 0.9 + k.organizers[s] * 60) * Week.F * (0.5 + k.enthusiasm);
                    if (valid(k, s) >= need[s] * 1.05) certify(g, k, s);
                }
                // deadlines that passed this turn
                if (g.day <= 0) for (int s = 0; s < NS; s++) {
                    if (k.onBallot[s] || deadline[s] != g.week - 1) continue;
                    if (valid(k, s) >= need[s]) certify(g, k, s);
                    else if (k.human) g.log(NK_BAD, s, "BALLOT: You missed the filing deadline in " + STATES[s].name + ". You'll be a write-in candidate there.");
                }
            }
        }

        static void certify(Game g, Camp k, int s) {
            double margin = valid(k, s) / Math.max(1, need[s]);
            if (margin < 1.25 && g.rng.nextDouble() < 0.18 * (1.3 - margin) / 0.3) {
                k.sigs[s] *= 0.7;
                if (k.human) g.log(NK_BAD, s, "BALLOT: A major-party lawsuit strikes thousands of your signatures in " + STATES[s].name + ". Gather more before the deadline.");
                return;
            }
            k.onBallot[s] = true;
            if (k.human) g.log(NK_GOOD, s, "BALLOT: " + k.last() + " qualifies for the ballot in " + STATES[s].name + " (" + count(k) + " states so far).");
        }

        static String status(Game g, Camp k, int s) {
            init();
            if (k.onBallot[s]) return "On the ballot";
            if (g.week > deadline[s]) return "Missed deadline (write-in)";
            return Math.min(99, Math.round(valid(k, s) / Math.max(1, need[s]) * 100)) + "% of signatures, due " + dateStr(deadline[s] * 7 + 1);
        }

        static int count(Camp k) { int n = 0; if (k.onBallot != null) for (boolean b : k.onBallot) if (b) n++; return n; }

        static int evOnBallot(Camp k) { int n = 0; if (k.onBallot != null) for (int s = 0; s < NS; s++) if (k.onBallot[s]) n += STATES[s].ev; return n; }

        /** The ballot director's plan: fund the signatures still needed, earliest deadlines first. */
        static void aiPlan(Game g, Camp k) {
            init();
            double budget = Math.max(0, k.cash - Week.hqWeek(g, k) * 2) * 0.5;
            for (int s = 0; s < NS; s++) {
                k.petitionW[s] = 0;
                if (k.onBallot[s] || g.week > deadline[s]) continue;
                double missing = need[s] * 1.3 / SIG_VALID - k.sigs[s];
                if (missing <= 0) continue;
                int weeks = Math.max(1, deadline[s] - 3 - g.week);       // aim to file about three weeks early
                double perWeek = missing * SIG_COST / Math.min(weeks, 10);
                k.petitionW[s] = Math.min(perWeek, budget);
                budget -= k.petitionW[s];
            }
        }
    }

    // =====================================================================
    //  20.  DIGITAL ADS, PODCASTS AND RALLIES
    // =====================================================================
    // ---- digital platforms -------------------------------------------------------------------------------------
    static final int NPLAT = 6;
    static final String[] PLAT = {"Facebook", "Instagram", "YouTube", "TikTok & creators", "Streaming TV", "Google Search"};
    static final String[] PLAT_DESC = {
            "Facebook: the oldest audience online. Strong with voters over 50 and in rural areas.",
            "Instagram: millennials and Gen Z, more women than men.",
            "YouTube (and Google video): under-45s, especially young men; pre-roll before sports, gaming and podcasts.",
            "TikTok and paid creators: TikTok bans political ads, so campaigns pay influencers. Reaches Gen Z like nothing else.",
            "Streaming TV (Hulu, Roku, YouTube TV): cord-cutters aged 30 and up. Pricey, but TV-quality spots.",
            "Google Search: people already looking. Cheap names for fundraising lists and strong for turnout reminders."};
    /** Cost per thousand impressions (political, 2024 averages rounded). */
    static final double[] PLAT_CPM = {14, 12, 20, 22, 40, 30};
    /** Reach multiplier by age group (18-29, 30-64, 65+); population-weighted average is about 1. */
    static final double[][] PLAT_AGE = {{0.45, 1.05, 1.6}, {1.8, 1.05, 0.3}, {1.5, 1.1, 0.55}, {2.7, 0.75, 0.12}, {0.6, 1.1, 1.15}, {1.0, 1.0, 0.8}};
    static final int DM_BIO = 0, DM_ISSUE = 1, DM_ATTACK = 2, DM_GOTV = 3, DM_FUND = 4, NDM = 5;
    static final String[] DMSG = {"Biography", "Issue focus", "Attack", "Get out the vote", "Fundraising"};
    static final String[] DMSG_SHORT = {"Bio", "Issue", "Attack", "GOTV", "Fund"};
    static final String[] DMSG_DESC = {
            "Introduce the candidate: who they are and what they've done. Builds name recognition and favourability.",
            "Your main issue focus. Persuades voters who care about it and builds your credibility on it.",
            "Hit your opponent with your current attack line. The most persuasive message - and it can backfire.",
            "Reminders to vote early or on Election Day. Little persuasion, but turns out your supporters in the final weeks.",
            "Ask for money. Builds your small-dollar list and raises some cash; persuades almost no one."};
    static final double[] DMSG_PERSUADE = {0.9, 1.0, 1.15, 0.25, 0.15};

    static final class Digital {
        static void defaults(Camp k) {
            k.digMix = new double[]{0.28, 0.16, 0.24, 0.10, 0.14, 0.08};
            k.digMsg = new int[]{DM_ISSUE, DM_BIO, DM_ISSUE, DM_BIO, DM_BIO, DM_FUND};
        }

        static void ensure(Camp k) { if (k.digMix == null || k.digMix.length != NPLAT || k.digMsg == null) defaults(k); }

        /** Blended cost per thousand impressions for the campaign's platform mix. */
        static double avgCpm(Camp k) {
            ensure(k);
            double t = 0, w = 0;
            for (int p = 0; p < NPLAT; p++) { t += k.digMix[p] * PLAT_CPM[p]; w += k.digMix[p]; }
            return w <= 0 ? 20 : t / w;
        }

        static void normalise(Camp k) {
            double t = 0;
            for (double v : k.digMix) t += v;
            if (t <= 0) { defaults(k); return; }
            for (int p = 0; p < NPLAT; p++) k.digMix[p] /= t;
        }

        /** Adds `step` to one platform's share, taking it proportionally from the others. */
        static void adjust(Camp k, int p, double step) {
            ensure(k);
            double nv = clamp(k.digMix[p] + step, 0, 1);
            double others = 0;
            for (int q = 0; q < NPLAT; q++) if (q != p) others += k.digMix[q];
            double left = 1 - nv;
            for (int q = 0; q < NPLAT; q++) if (q != p) k.digMix[q] = others > 0 ? k.digMix[q] / others * left : left / (NPLAT - 1);
            k.digMix[p] = nv;
        }

        /** Share of a campaign's digital impressions reaching each age group. */
        static double[] ageProfile(Camp k) {
            ensure(k);
            double[] a = new double[NAGE];
            double[] pop = {0.20, 0.57, 0.23};
            double t = 0;
            for (int g = 0; g < NAGE; g++) { for (int p = 0; p < NPLAT; p++) a[g] += k.digMix[p] * PLAT_AGE[p][g] * pop[g]; t += a[g]; }
            for (int g = 0; g < NAGE; g++) a[g] /= Math.max(1e-9, t);
            return a;
        }

        /** Spends the week's digital budget in a state; returns dollars spent on each message (national totals accumulate in tot). */
        static void spend(Game g, Camp k, int s, double dollars, double quality, double[] tot) {
            ensure(k);
            double adults = Math.max(1, Week.sumCvap(s));
            int left = Week.weeksToVote(g, k, s);
            for (int p = 0; p < NPLAT; p++) {
                double d = dollars * k.digMix[p];
                if (d <= 0) continue;
                int msg = k.digMsg[p];
                double perAdult = d / PLAT_CPM[p] * 1000 / adults;
                for (int a = 0; a < NAGE; a++) k.digA[s][a] += perAdult * PLAT_AGE[p][a] * DMSG_PERSUADE[msg] * quality * 100;
                if (msg == DM_GOTV && left <= 6) k.gotv[s] += perAdult * 0.0005 * (p == 5 ? 1.4 : 1.0);
                tot[msg] += d;
            }
        }

        /** National effects of the week's digital messages (credibility, attacks, fundraising). */
        static void effects(Game g, Camp k, double[] tot) {
            double F = Week.F;
            if (tot[DM_ISSUE] > 0) {
                double v = Math.log1p(tot[DM_ISSUE] / F / 1e6);
                Issues.build(k, k.focusIssue, 0.004 * v * F);
                for (int i : k.focus2) if (i >= 0) Issues.build(k, i, 0.0015 * v * F);
            }
            if (tot[DM_ATTACK] > 0) {
                Camp t = Message.attackTarget(g, k);
                if (t != null) {
                    int line = k.attack > 0 ? k.attack : bestAttack(g, t);
                    double v = Math.log1p(tot[DM_ATTACK] / F / 1e6);
                    t.attackStick[line] += 0.02 * v * Message.attackFit(g, t, line) * F;
                    k.negativity += 0.01 * v * F;
                }
            }
            if (tot[DM_BIO] > 0) k.favor += 0.0006 * Math.log1p(tot[DM_BIO] / F / 1e6) * F;
            if (tot[DM_FUND] > 0) {
                k.smallList += tot[DM_FUND] / 6.5 * (0.5 + k.a(A_SMD) / 100.0);
                Week.raise(k, tot[DM_FUND] * 0.35 * (0.6 + k.a(A_SMD) / 125.0), RS_SMALL);
            }
            double other = tot[DM_BIO] + tot[DM_ISSUE] + tot[DM_ATTACK] + tot[DM_GOTV];
            k.smallList += other / 60.0;
        }

        static int bestAttack(Game g, Camp t) {
            int best = 1;
            for (int a = 1; a < ATTACK.length; a++) if (Message.attackFit(g, t, a) > Message.attackFit(g, t, best)) best = a;
            return best;
        }

        /** The computer's (and the ad team's) platform mix and messages. */
        static void aiPlan(Game g, Camp k) {
            ensure(k);
            boolean young = k.c.faction == F_DSOC || k.c.faction == F_PROG || k.c.faction == F_TECHR || k.c.faction == F_RESTR || k.party == IND || k.a(A_VIR) > 80;
            k.digMix = young ? new double[]{0.18, 0.20, 0.26, 0.18, 0.10, 0.08} : new double[]{0.32, 0.14, 0.22, 0.06, 0.18, 0.08};
            int left = g.phase >= PH_CONVENTION || k.nominee ? ELECTION_WEEK - g.week : AI.nextBigContest(g, k) - g.week;
            int main;
            if (left <= 3) main = DM_GOTV;
            else if (left <= 9 && k.aggression > 0.45) main = DM_ATTACK;
            else if (g.phase == PH_INVISIBLE) main = DM_BIO;
            else main = DM_ISSUE;
            for (int p = 0; p < NPLAT; p++) k.digMsg[p] = main;
            k.digMsg[5] = left <= 3 ? DM_GOTV : DM_FUND;
            if (main == DM_ATTACK) k.digMsg[1] = DM_BIO;
        }
    }

    // ---- podcasts and long-form interviews ------------------------------------------------------------------
    static final class Show {
        final String name, audience;
        final int tier;
        final double views, lean;
        final int[] g;
        final double[] w;

        Show(String name, String audience, int tier, double views, double lean, String groups) {
            this.name = name; this.audience = audience; this.tier = tier; this.views = views; this.lean = lean;
            String[] t = groups.split(" ");
            g = new int[t.length]; w = new double[t.length];
            for (int i = 0; i < t.length; i++) { String[] p = t[i].split(":"); g[i] = Integer.parseInt(p[0]); w[i] = Double.parseDouble(p[1]); }
        }
    }

    static final String[] TIER_NAME = {"Small", "Mid-size", "Top tier"};
    /** National name recognition needed to be booked, by tier. */
    static final double[] TIER_NEED = {0, 0.15, 0.35};

    // groups: 0 GenZ 1 Seniors 2 YoungMen 3 YoungWomen 4 Women 5 Men 6 Evang 7 Cath 8 WhiteNoDeg 9 WhiteDeg 10 Black 11 BlackMen
    // 12 Hisp 13 HispMen 14 Cuban 15 PR 16 Asian 17 Jewish 18 Arab 19 LDS 20 Native 21 Suburb 22 Rural 23 Urban 24 Union
    // 25 Vets 26 Gun 27 LGBTQ 28 Students 29 Parents
    /** Real shows. Typical views per candidate episode are the game's rough estimates; lean: - left, + right. */
    static final Show[] SHOWS = {
            new Show("The Joe Rogan Experience", "Comedy, MMA and long-form talk; young men", 2, 25, 0.35, "2:1 5:0.6 8:0.4 22:0.3"),
            new Show("This Past Weekend w/ Theo Von", "Comedy; young working-class men", 2, 12, 0.35, "2:1 8:0.5 22:0.4"),
            new Show("Flagrant", "Comedy roundtable; young men, many Black and Hispanic", 2, 10, 0.25, "2:1 11:0.5 13:0.5"),
            new Show("Full Send Podcast", "Pranks, sports and influencers; very young men", 2, 10, 0.5, "2:1 0:0.5"),
            new Show("Club Shay Shay", "Sports and culture; Black audiences", 2, 9, 0.0, "10:1 11:0.8 5:0.3"),
            new Show("Impaulsive", "Influencer talk show; young men", 2, 6, 0.3, "2:1 0:0.5"),
            new Show("Lex Fridman Podcast", "Science, tech and four-hour interviews", 2, 6, 0.1, "9:0.6 2:0.5 16:0.3"),
            new Show("Call Her Daddy", "Relationships and culture; young women", 2, 5, -0.4, "3:1 0:0.5 4:0.3"),
            new Show("The Diary of a CEO", "Business and self-improvement", 2, 5, 0.0, "9:0.4 0:0.4 29:0.3"),
            new Show("New Heights", "Football and family; sports fans and women", 2, 4, -0.1, "4:0.5 5:0.5 21:0.3"),
            new Show("The Breakfast Club", "Hip-hop radio and video; Black listeners", 1, 2.5, -0.3, "10:1 11:0.6 23:0.4"),
            new Show("The Shawn Ryan Show", "Veterans, security and survival", 1, 3, 0.5, "25:1 26:0.6 5:0.4"),
            new Show("SmartLess", "Celebrity comedy; affluent suburbanites", 1, 2, -0.4, "21:0.6 9:0.6 4:0.3"),
            new Show("Bussin' With The Boys", "Sports and beer; young men", 1, 2, 0.4, "2:0.8 5:0.5 22:0.3"),
            new Show("All-In", "Tech investors on business and politics", 1, 1.5, 0.45, "9:0.6 16:0.3 5:0.3"),
            new Show("The Megyn Kelly Show", "Conservative news and talk", 1, 1.5, 0.8, "6:0.5 4:0.4 1:0.4"),
            new Show("The Ben Shapiro Show", "Conservative news and commentary", 1, 1.5, 0.9, "6:0.6 5:0.4 17:0.3"),
            new Show("The Bill Simmons Podcast", "Sports and pop culture", 1, 1.2, -0.1, "5:0.7 9:0.4"),
            new Show("Pod Save America", "Progressive politics", 1, 1.0, -0.9, "0:0.4 9:0.6 23:0.4"),
            new Show("The Ezra Klein Show", "Policy and ideas", 1, 0.8, -0.7, "9:0.8 23:0.3"),
            new Show("Breaking Points", "Populist news from the left and the right", 0, 0.6, 0.0, "8:0.5 0:0.4"),
            new Show("The Bulwark Podcast", "Anti-Trump conservatives and independents", 0, 0.4, -0.5, "21:0.6 9:0.5"),
            new Show("Higher Learning", "Black culture and politics", 0, 0.4, -0.4, "10:0.8 0:0.4"),
            new Show("I've Had It", "Progressive anger and humour", 0, 0.4, -0.9, "4:0.5 23:0.3"),
            new Show("Honestly with Bari Weiss", "Heterodox ideas", 0, 0.35, 0.2, "9:0.5 17:0.4"),
            new Show("Pablo Torre Finds Out", "Sports investigations", 0, 0.3, -0.1, "5:0.5 0:0.3"),
            new Show("Hard Fork", "Technology news", 0, 0.25, -0.3, "9:0.5 0:0.3"),
            new Show("A local politics podcast", "Your current state's political junkies", 0, 0.05, 0.0, "21:0.3 9:0.3")};
    static final int LOCAL_SHOW = 27;

    static final class Podcasts {
        static double sign(Camp k) { return k.party == DEM || k.party == GRN || k.party == FWD ? -1 : k.party == REP || k.party == LBT ? 1 : 0; }

        /** Weeks before a show will have the same guest back. */
        static final int[] REBOOK = {3, 8, 20};

        /** Can the candidate get booked? null if yes, otherwise the reason. */
        static String unavailable(Game g, Camp k, int show) {
            Show s = SHOWS[clampi(show, 0, SHOWS.length - 1)];
            if (k.nameId < TIER_NEED[s.tier]) return "Needs " + Math.round(TIER_NEED[s.tier] * 100) + "% national name ID (you have " + Math.round(k.nameId * 100) + "%)";
            double opp = -sign(k) * s.lean;
            if (opp >= 0.75 && k.a(A_XOV) < 70) return "The host won't book you (crossover appeal 70+ needed)";
            if (show != LOCAL_SHOW && g != null)
                for (int i = k.media.size() - 1; i >= 0; i--) {
                    Appearance a = k.media.get(i);
                    if (a.show != show || a.declined) continue;
                    int back = a.week + REBOOK[s.tier];
                    if (g.week < back) return "You were just on (" + s.name + " will have you back from " + dateStr(back * 7).replaceAll(", 20\\d\\d", "") + ")";
                    break;
                }
            return null;
        }

        /** The computer's choice of show: the biggest available one whose audience it wants. */
        static int aiPick(Game g, Camp k) { return aiPick(g, k, null); }

        /** As above, skipping shows already booked in this week's plan. */
        static int aiPick(Game g, Camp k, boolean[] booked) {
            int best = LOCAL_SHOW;
            double bv = -1e9;
            for (int i = 0; i < SHOWS.length; i++) {
                if (unavailable(g, k, i) != null || i == LOCAL_SHOW || booked != null && booked[i]) continue;
                Show s = SHOWS[i];
                double fit = -Math.abs(s.lean - sign(k) * 0.4) * 0.8;
                double v = Math.log(s.views) + fit + g.rng.nextDouble() * 0.8;
                if (v > bv) { bv = v; best = i; }
            }
            if (booked != null) booked[best] = best != LOCAL_SHOW;
            return best;
        }

        /** An appearance: views, reviews, the audience's verdict, name ID and small-dollar donors. */
        static Appearance appear(Game g, Camp k, int show, double eff) {
            Show s = SHOWS[clampi(show, 0, SHOWS.length - 1)];
            Appearance ap = new Appearance();
            ap.week = g.week; ap.day = g.day; ap.show = show;
            String why = unavailable(g, k, show);
            if (why != null) {
                ap.declined = true;
                k.media.add(ap);
                if (k.human) g.log(NK_BAD, "PODCAST: " + s.name + " passes on booking " + k.last() + ". " + why + ".");
                return ap;
            }
            double vir = k.a(A_VIR) / 100.0, pod = k.a(A_POD) / 100.0, aut = k.a(A_AUT) / 100.0;
            double opp = -sign(k) * s.lean;
            boolean cross = opp > 0.25;
            double views = s.views * 1e6 * (0.5 + 0.6 * vir + 0.5 * k.nameId) * Math.exp(g.rng.nextGaussian() * 0.45) * (cross ? 1.1 : 1.0) * (g.phase <= PH_PRIMARY && !k.nominee ? 0.8 : 1.0);
            if (show == LOCAL_SHOW) { views = (20000 + 60000 * g.rng.nextDouble()) * (0.6 + vir); ap.state = k.location; }
            double q = (pod - 0.5) * 1.2 + (aut - 0.5) * 0.8 + g.rng.nextGaussian() * 0.35 - (k.stamina < 40 ? 0.25 : 0) + (cross ? -0.1 : 0.1);
            q = clamp(q, -1.2, 1.2) * eff;
            double reach = views / 2.5e8;
            double img = reach * (0.6 + q) * 0.35 * (cross ? 1.4 : 1.0);
            for (int i = 0; i < s.g.length; i++) k.imageG[s.g[i]] += img * s.w[i];
            if (show == LOCAL_SHOW) NameId.learnState(k, k.location, 0.03);
            else {
                double before = k.nameId;
                NameId.learn(k, views * 0.22 / 2.5e8);      // unique American viewers who didn't know the candidate
                ap.nameGain = k.nameId - before;
            }
            ap.signups = views * 0.0012 * (0.5 + k.a(A_SMD) / 100.0) * Math.max(0.15, 0.5 + q);
            ap.raised = ap.signups * 18 * 0.35;
            k.smallList += ap.signups;
            Week.raise(k, ap.raised, RS_SMALL);
            if (q > 0.3) k.momentum += Math.min(0.06, reach * 3 * q);
            if (q < -0.7) { k.favor -= 0.004; k.attackStick[10] += 0.03; }
            ap.views = views;
            ap.quality = q;
            if (show != LOCAL_SHOW && g.rng.nextDouble() < 0.04 + 0.16 * vir * Math.max(0, q)) { ap.viral = true; Week.viral(g, k, "a clip from " + s.name); }
            k.media.add(ap);
            if (k.media.size() > 160) k.media.remove(0);
            String verdict = q > 0.6 ? "rave reviews" : q > 0.15 ? "good reviews" : q > -0.3 ? "mixed reviews" : q > -0.7 ? "an awkward hour" : "a rough interview that critics pounce on";
            if (k.human) g.log(q > -0.3 ? NK_GOOD : NK_BAD, "PODCAST: " + k.last() + " on " + s.name + " - " + compact(views) + " views, " + verdict + ". +" + compact(ap.signups) + " new donors (" + money(ap.raised) + ").");
            else if ((k.nominee || g.primaryPoll != null && k.idx < g.primaryPoll.length && g.primaryPoll[k.idx] > 0.12) && s.tier == 2)
                g.log(NK_INFO, "PODCAST: " + k.name() + " sits down with " + s.name + " (" + compact(views) + " views).");
            return ap;
        }
    }

    // ---- rallies ---------------------------------------------------------------------------------------------
    static final class Rallies {
        /** A typical crowd for a well-run rally at this stage of the race. */
        static double reference(Game g, Camp k) {
            if (g.phase >= PH_CONVENTION && k.nominee) return k.party <= REP ? 12000 : 2500;
            if (g.phase == PH_PRIMARY) return 2500;
            return k.announced ? 900 : 300;
        }

        static String city(int s) {
            List<Integer> ms = Elect.marketsIn(s);
            for (int m : ms) {
                String n = Elect.marketName[m];
                if (n.startsWith("Rest of")) continue;
                int dash = n.indexOf('-');
                int sp = n.indexOf(" (");
                String c = dash > 0 ? n.substring(0, dash) : sp > 0 ? n.substring(0, sp) : n;
                c = c.replace(" et al.", "").trim();
                return c;
            }
            return STATES[s].name;
        }

        static String venue(double crowd) {
            return crowd < 600 ? "a VFW hall" : crowd < 1800 ? "a high-school gym" : crowd < 6000 ? "a convention center" : crowd < 20000 ? "an arena" : "a stadium";
        }

        /** Holds a rally; returns the crowd relative to the reference (about 1 = a good rally). */
        static double hold(Game g, Camp k, int s, double eff) {
            double cha = k.a(A_CHA) / 100, spe = k.a(A_SPE) / 100;
            double urban = STATES[s].urban / 100.0;
            double size = Math.sqrt(Week.sumCvap(s) / 4.0e6);
            double crowd = reference(g, k) * (0.45 + cha * 0.6 + spe * 0.3) * (0.55 + k.enthusiasm) * (0.35 + 0.75 * k.nameIdS[s])
                    * (0.6 + 0.5 * urban) * clamp(0.6 + 0.4 * size, 0.6, 1.6) * (0.85 + 0.3 * clamp(k.momentum, -0.5, 1))
                    * (k.hq == 0 ? 0.88 : k.hq == 2 ? 1.1 : 1.0) * Math.exp(g.rng.nextGaussian() * 0.25) * eff;
            if (s == k.c.home) crowd *= 1.25;
            crowd = Math.max(60, Math.round(crowd / 10.0) * 10);
            double best = 0;
            for (Rally o : k.rallies) best = Math.max(best, o.crowd);
            Rally r = new Rally();
            r.week = g.week; r.day = g.day; r.state = s; r.crowd = crowd; r.venue = venue(crowd);
            k.rallies.add(r);
            if (k.rallies.size() > 300) k.rallies.remove(0);
            k.rallyCount++;
            k.rallyCrowds += crowd;
            double rel = crowd / reference(g, k);
            if (k.human) {
                if (crowd > best && k.rallyCount > 3 && crowd >= 1000) g.log(NK_GOOD, s, "RALLY: " + thousands(Math.round(crowd)) + " pack " + r.venue + " in " + city(s) + " - your biggest crowd yet.");
                else if (rel < 0.45) g.log(NK_BAD, s, "RALLY: A sparse crowd of " + thousands(Math.round(crowd)) + " in " + city(s) + "; reporters notice the empty seats.");
            } else if (k.nominee && k.party <= REP && rel > 1.6 && g.rng.nextDouble() < 0.3)
                g.log(NK_INFO, s, "RALLY: " + k.last() + " draws " + thousands(Math.round(crowd)) + " in " + city(s) + ".");
            if (rel < 0.45) k.favor -= 0.002;
            return clamp(rel, 0.3, 2.2);
        }
    }

    // =====================================================================
    //  20.  USER INTERFACE: window, input, widgets
    // =====================================================================
    static final int SC_HOME = 0, SC_SETUP = 1, SC_PICK = 2, SC_CREATE = 3, SC_HQ = 4, SC_DEBATE = 5, SC_NIGHT = 6,
            SC_END = 7, SC_BRIEF = 8, SC_VP = 9, SC_NEGOT = 10, SC_PLATFORM = 11, SC_FIELD = 12, SC_LOADING = 13, SC_RESULTS = 14;

    static final String SANS = pickFont(new String[]{"Inter", "Segoe UI", "Helvetica Neue", "SF Pro Text", "Roboto", "Noto Sans",
            "Open Sans", "Lato", "DejaVu Sans", "Liberation Sans", "Arial"}, "SansSerif");
    static final String SERIF = pickFont(new String[]{"Georgia", "Playfair Display", "Merriweather", "Cambria", "DejaVu Serif",
            "Liberation Serif", "Times New Roman"}, "Serif");
    static final String MONO = pickFont(new String[]{"JetBrains Mono", "Cascadia Mono", "Consolas", "Menlo", "SF Mono",
            "DejaVu Sans Mono", "Liberation Mono", "Courier New"}, "Monospaced");

    static String pickFont(String[] want, String fallback) {
        try {
            String[] have = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
            for (String w : want) for (String h : have) if (h.equalsIgnoreCase(w)) return h;
        } catch (Throwable t) { /* headless or no fonts */ }
        return fallback;
    }

    static final Font F_HERO = new Font(SERIF, Font.BOLD, 78);
    static final Font F_DISPLAY = new Font(SERIF, Font.BOLD, 40);
    static final Font F_SERIF = new Font(SERIF, Font.BOLD, 24);
    static final Font F_SERIF_S = new Font(SERIF, Font.ITALIC, 15);
    static final Font F_H1 = new Font(SANS, Font.BOLD, 24);
    static final Font F_H2 = new Font(SANS, Font.BOLD, 17);
    static final Font F_H3 = new Font(SANS, Font.BOLD, 13);
    static final Font F_B = new Font(SANS, Font.PLAIN, 13);
    static final Font F_BB = new Font(SANS, Font.BOLD, 13);
    static final Font F_S = new Font(SANS, Font.PLAIN, 12);
    static final Font F_SB = new Font(SANS, Font.BOLD, 12);
    static final Font F_XS = new Font(SANS, Font.PLAIN, 11);
    static final Font F_XSB = new Font(SANS, Font.BOLD, 11);
    static final Font F_T = new Font(SANS, Font.PLAIN, 10);
    static final Font F_TB = new Font(SANS, Font.BOLD, 10);
    static final Font F_M = new Font(MONO, Font.PLAIN, 12);
    static final Font F_MB = new Font(MONO, Font.BOLD, 12);
    static final Font F_MS = new Font(MONO, Font.PLAIN, 10);
    static final Font F_NUM = new Font(SANS, Font.BOLD, 21);
    static final Font F_NUMXL = new Font(SANS, Font.BOLD, 34);

    interface DFn { void on(double v); }

    interface IFn { void on(int i); }

    interface SFn { void on(String s); }

    interface Painter { void paint(Graphics2D g, double x, double y, double w, double h); }

    static Battleground2028 APP;
    JFrame frame;
    javax.swing.Timer timer;
    int screen = SC_HOME, prevScreen = SC_HOME;
    double screenT;                         // seconds since the screen changed (for intro animations)
    Game game;
    Settings setup = new Settings();
    double sc = 1, ox, oy;
    int winW = DW, winH = DH;
    double mx = -1, my = -1, pressX, pressY;
    boolean pressed;
    double tsec;
    long lastNanos;

    static final class Hit {
        double x, y, w, h;
        Runnable act;
        DFn dragX;
        double dragX0, dragW;
        String tip, lab;
        int layer;

        boolean in(double px, double py) { return px >= x && py >= y && px < x + w && py < y + h; }
    }

    final List<Hit> hits = new ArrayList<Hit>();
    final Map<String, double[]> scrolls = new HashMap<String, double[]>();   // id -> {x,y,w,h,offset,max,target}
    String tip;
    Hit dragging;
    int layer;                              // 0 normal, 1 popup, 2 modal: only the top layer takes clicks
    int topLayer;

    // text editing
    String editId;
    StringBuilder editBuf = new StringBuilder();
    SFn editSet;

    // toasts
    static final class Toast { String text; Color col; double t0; }

    final List<Toast> toasts = new ArrayList<Toast>();

    void toast(String s, Color c) {
        Toast t = new Toast();
        t.text = s; t.col = c == null ? C_ACCENT : c; t.t0 = tsec;
        toasts.add(t);
        while (toasts.size() > 5) toasts.remove(0);
    }

    // modal
    static final class Modal {
        String title, body;
        String[] buttons, descs;
        Runnable[] actions;
        Color col = C_ACCENT;
        Painter painter;
        double w = 660, extraH;
        boolean stacked;                     // options as full-width stacked buttons with descriptions
        Cand face;                           // portrait shown at the left
    }

    Modal modal;
    final List<Modal> modalQueue = new ArrayList<Modal>();

    // popup menu
    static final class Popup {
        double x, y, w;
        String title;
        String[] items, tips, right;
        Color[] cols;
        boolean[] enabled;
        IFn pick;
        int hover = -1;
    }

    Popup popup;

    Battleground2028() {
        setPreferredSize(new Dimension(1440, 810));
        setBackground(C_BG);
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        addMouseListener(this);
        addMouseMotionListener(this);
        addMouseWheelListener(this);
        addKeyListener(this);
        timer = new javax.swing.Timer(16, this);
        lastNanos = System.nanoTime();
    }

    static void launch() {
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception e) { /* default */ }
        final Battleground2028 p = new Battleground2028();
        APP = p;
        JFrame f = new JFrame("Battleground 2028 - The Race for the White House");
        p.frame = f;
        f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        f.addWindowListener(new WindowAdapter() {
            public void windowClosed(WindowEvent e) { p.timer.stop(); if (sfx != null) sfx.stop(); }
        });
        f.add(p);
        f.pack();
        Dimension scr = Toolkit.getDefaultToolkit().getScreenSize();
        int w = Math.min(1600, scr.width - 60), h = Math.min(900, scr.height - 80);
        double ar = DW / (double) DH;
        if (w / ar > h) w = (int) (h * ar); else h = (int) (w / ar);
        f.setSize(Math.max(960, w), Math.max(560, h + 28));
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        p.requestFocusInWindow();
        sfx = new Sfx();
        sfx.start();
        new Thread(new Runnable() { public void run() { Elect.init(); USMap.load(); } }, "bg-loader").start();
        p.timer.start();
    }

    public void actionPerformed(ActionEvent e) {
        long now = System.nanoTime();
        double dt = Math.min(0.1, (now - lastNanos) / 1e9);
        lastNanos = now;
        tsec += dt;
        screenT += dt;
        update(dt);
        repaint();
    }

    void go(int s) {
        if (s != screen) { prevScreen = screen; screen = s; screenT = 0; popup = null; editId = null; }
    }

    void update(double dt) {
        if (screen == SC_HQ && game != null) Hq.update(this, dt);
        if (screen == SC_NIGHT && game != null) Night.update(this, dt);
        if (screen == SC_DEBATE) DebateView.update(this, dt);
        if (screen == SC_LOADING) Loading.update(this);
        USMap.animate(dt);
        for (double[] s : scrolls.values()) if (s.length > 6) s[4] += (s[6] - s[4]) * Math.min(1, dt * 14);
    }

    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        winW = getWidth();
        winH = getHeight();
        render(g, winW, winH);
    }

    void render(Graphics2D g, int w, int h) {
        g.setColor(Color.black);
        g.fillRect(0, 0, w, h);
        sc = Math.min(w / (double) DW, h / (double) DH);
        ox = (w - DW * sc) / 2;
        oy = (h - DH * sc) / 2;
        AffineTransform base = g.getTransform();
        g.translate(ox, oy);
        g.scale(sc, sc);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setClip(0, 0, DW, DH);
        hits.clear();
        tip = null;
        layer = 0;
        topLayer = modal != null ? 2 : popup != null ? 1 : 0;
        try {
            switch (screen) {
                case SC_HOME: Screens.home(this, g); break;
                case SC_SETUP: Screens.setup(this, g); break;
                case SC_PICK: Screens.pick(this, g); break;
                case SC_CREATE: Creator.draw(this, g); break;
                case SC_PLATFORM: Screens.platform(this, g); break;
                case SC_HQ: Hq.draw(this, g); break;
                case SC_DEBATE: DebateView.draw(this, g); break;
                case SC_NIGHT: Night.draw(this, g); break;
                case SC_END: Night.end(this, g); break;
                case SC_BRIEF: Screens.briefing(this, g); break;
                case SC_VP: Screens.vp(this, g); break;
                case SC_NEGOT: Screens.negotiate(this, g); break;
                case SC_FIELD: FieldScreen.draw(this, g); break;
                case SC_LOADING: Loading.draw(this, g); break;
                case SC_RESULTS: Results.draw(this, g); break;
                default: break;
            }
        } catch (RuntimeException ex) {
            g.setClip(0, 0, DW, DH);
            text(g, "Display error: " + ex, 20, DH - 20, F_XS, C_BAD);
            if (System.getProperty("bg.debug") != null) ex.printStackTrace();
        }
        g.setClip(0, 0, DW, DH);
        drawToasts(g);
        if (popup != null) { layer = 1; drawPopup(g); layer = 0; }
        if (modal == null && !modalQueue.isEmpty()) modal = modalQueue.remove(0);
        if (modal != null) { layer = 2; drawModal(g); layer = 0; }
        try { Tutorial.draw(this, g); } catch (RuntimeException ex) { if (System.getProperty("bg.debug") != null) ex.printStackTrace(); }
        if (tip != null && dragging == null) drawTip(g, tip);
        g.setTransform(base);
    }

    // ---- input ------------------------------------------------------------------------------------------
    double dX(MouseEvent e) { return (e.getX() - ox) / sc; }

    double dY(MouseEvent e) { return (e.getY() - oy) / sc; }

    public void mousePressed(MouseEvent e) {
        requestFocusInWindow();
        mx = dX(e); my = dY(e);
        pressX = mx; pressY = my;
        pressed = true;
        if (editId != null) commitEdit();
        if (SwingUtilities.isRightMouseButton(e)) {
            if (popup != null) { popup = null; return; }
            if (modal == null && screen == SC_HQ) Hq.rightClick(this, mx, my);
            return;
        }
        int top = modal != null ? 2 : popup != null ? 1 : 0;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.layer != top) continue;
            if (h.in(mx, my)) {
                if (h.dragX != null) { dragging = h; h.dragX.on(clamp((mx - h.dragX0) / h.dragW, 0, 1)); return; }
                if (h.act != null) { snd(Sfx.CLICK, 0.6, 1); h.act.run(); }
                return;
            }
        }
        if (top == 1) { popup = null; return; }
        if (top == 2) return;
        if (screen == SC_HQ) Hq.mapPress(this, mx, my);
    }

    public void mouseReleased(MouseEvent e) {
        mx = dX(e); my = dY(e);
        pressed = false;
        dragging = null;
    }

    public void mouseDragged(MouseEvent e) {
        double nx = dX(e), ny = dY(e);
        if (dragging != null && dragging.dragX != null) dragging.dragX.on(clamp((nx - dragging.dragX0) / dragging.dragW, 0, 1));
        else {
            for (double[] s : scrolls.values()) {
                if (pressX >= s[0] + s[2] - 12 && pressX <= s[0] + s[2] + 2 && pressY >= s[1] && pressY <= s[1] + s[3] && s[5] > 0) {
                    s[6] = clamp(s[6] + (ny - my) * (s[5] + s[3]) / s[3], 0, s[5]);
                    s[4] = s[6];
                }
            }
        }
        mx = nx; my = ny;
    }

    public void mouseMoved(MouseEvent e) { mx = dX(e); my = dY(e); }

    public void mouseWheelMoved(MouseWheelEvent e) {
        double x = dX(e), y = dY(e);
        if (modal != null || popup != null) return;
        double[] best = null;
        double area = 1e18;
        for (double[] s : scrolls.values()) {
            if (x >= s[0] && y >= s[1] && x < s[0] + s[2] && y < s[1] + s[3] && s[2] * s[3] < area && s[5] > 0) { best = s; area = s[2] * s[3]; }
        }
        if (best != null) best[6] = clamp(best[6] + e.getPreciseWheelRotation() * 48, 0, Math.max(0, best[5]));
    }

    public void mouseClicked(MouseEvent e) {}

    public void mouseEntered(MouseEvent e) {}

    public void mouseExited(MouseEvent e) { mx = -1; my = -1; }

    public void keyTyped(KeyEvent e) {
        if (editId == null) return;
        char c = e.getKeyChar();
        if (c >= 32 && c < 127 && editBuf.length() < 28) { editBuf.append(c); if (editSet != null) editSet.on(editBuf.toString()); }
    }

    public void keyReleased(KeyEvent e) {}

    public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();
        if (editId != null) {
            if (k == KeyEvent.VK_BACK_SPACE && editBuf.length() > 0) { editBuf.setLength(editBuf.length() - 1); if (editSet != null) editSet.on(editBuf.toString()); }
            if (k == KeyEvent.VK_ENTER || k == KeyEvent.VK_ESCAPE || k == KeyEvent.VK_TAB) commitEdit();
            return;
        }
        if (modal != null) {
            if (k == KeyEvent.VK_ESCAPE && modal.buttons != null && modal.buttons.length == 1) closeModal();
            if (k == KeyEvent.VK_ENTER && modal.actions != null && modal.actions.length == 1) { Runnable r = modal.actions[0]; closeModal(); if (r != null) r.run(); }
            return;
        }
        if (popup != null) { if (k == KeyEvent.VK_ESCAPE) popup = null; return; }
        if (k == KeyEvent.VK_F1) { Screens.openBriefing(this); return; }
        switch (screen) {
            case SC_HQ: Hq.key(this, e); break;
            case SC_BRIEF: if (k == KeyEvent.VK_ESCAPE) go(Screens.briefReturn); break;
            case SC_NIGHT: Night.key(this, e); break;
            case SC_DEBATE: DebateView.key(this, e); break;
            case SC_SETUP: case SC_PICK: case SC_CREATE: case SC_PLATFORM: case SC_FIELD:
                if (k == KeyEvent.VK_ESCAPE) Screens.back(this);
                break;
            default: break;
        }
    }

    void commitEdit() { editId = null; }

    // ---- widgets ----------------------------------------------------------------------------------------
    boolean hover(double x, double y, double w, double h) {
        return dragging == null && layer == topLayer && mx >= x && my >= y && mx < x + w && my < y + h;
    }

    Hit hit(double x, double y, double w, double h, Runnable a, String t) {
        Hit hh = new Hit();
        hh.x = x; hh.y = y; hh.w = w; hh.h = h; hh.act = a; hh.tip = t; hh.layer = layer;
        hits.add(hh);
        if (t != null && hover(x, y, w, h)) tip = t;
        return hh;
    }

    /** A horizontal drag region (sliders). */
    void dragRegion(double x, double y, double w, double h, double x0, double ww, DFn f, String t) {
        Hit hh = hit(x, y, w, h, null, t);
        hh.dragX = f; hh.dragX0 = x0; hh.dragW = ww;
    }

    static final int B_NORM = 0, B_PRIMARY = 1, B_DANGER = 2, B_ON = 3, B_GHOST = 4, B_TAB = 5, B_TABON = 6, B_OFF = 7, B_GOLD = 8,
            B_DEM = 9, B_REP = 10, B_SUBTLE = 11;

    boolean button(Graphics2D g, double x, double y, double w, double h, String label, int style, Runnable act) {
        return button(g, x, y, w, h, label, style, act, null);
    }

    boolean button(Graphics2D g, double x, double y, double w, double h, String label, int style, Runnable act, String tipText) {
        boolean dis = act == null || style == B_OFF;
        boolean hov = !dis && hover(x, y, w, h);
        boolean down = hov && pressed;
        Color edge, top, bot, txt;
        switch (style) {
            case B_PRIMARY: edge = rgb(0x5aa9ff); top = rgb(0x2f6fe0); bot = rgb(0x1d4fb0); txt = Color.white; break;
            case B_GOLD: edge = rgb(0xffd77a); top = rgb(0xf2c14e); bot = rgb(0xd49a1f); txt = rgb(0x1d1405); break;
            case B_DANGER: edge = rgb(0xff7b7b); top = rgb(0xc93a3f); bot = rgb(0x8f2228); txt = Color.white; break;
            case B_ON: edge = rgb(0x5be39a); top = rgb(0x1f8f55); bot = rgb(0x136b3e); txt = Color.white; break;
            case B_DEM: edge = C_DEM_L; top = rgb(0x2f6fe0); bot = C_DEM_D; txt = Color.white; break;
            case B_REP: edge = C_REP_L; top = rgb(0xd9434a); bot = C_REP_D; txt = Color.white; break;
            case B_GHOST: edge = new Color(120, 150, 200, 90); top = new Color(255, 255, 255, 8); bot = new Color(255, 255, 255, 4); txt = C_TEXT; break;
            case B_SUBTLE: edge = new Color(255, 255, 255, 0); top = new Color(255, 255, 255, 10); bot = new Color(255, 255, 255, 6); txt = C_DIM; break;
            case B_TAB: edge = new Color(80, 110, 160, 0); top = new Color(255, 255, 255, 0); bot = new Color(255, 255, 255, 0); txt = C_DIM; break;
            case B_TABON: edge = new Color(124, 196, 255, 0); top = new Color(124, 196, 255, 34); bot = new Color(124, 196, 255, 18); txt = Color.white; break;
            case B_OFF: edge = new Color(70, 80, 100, 120); top = new Color(30, 36, 48, 200); bot = new Color(24, 28, 38, 200); txt = C_FAINT; break;
            default: edge = new Color(90, 120, 170, 160); top = rgb(0x1c2a44); bot = rgb(0x152036); txt = C_TEXT; break;
        }
        if (dis && style != B_TAB && style != B_TABON) { edge = new Color(70, 80, 100, 110); top = new Color(30, 36, 48, 190); bot = new Color(24, 28, 38, 190); txt = C_FAINT; }
        if (hov) { top = shade(top, style == B_GHOST || style == B_TAB || style == B_SUBTLE ? 1.0 : 1.18); bot = shade(bot, 1.15); edge = shade(edge, 1.2); if (style == B_TAB || style == B_GHOST || style == B_SUBTLE) { top = new Color(255, 255, 255, 22); bot = new Color(255, 255, 255, 14); txt = Color.white; } }
        if (down) { Color t = top; top = bot; bot = t; }
        RoundRectangle2D rr = new RoundRectangle2D.Double(x, y, w, h, 8, 8);
        g.setPaint(new GradientPaint((float) x, (float) y, top, (float) x, (float) (y + h), bot));
        g.fill(rr);
        if (style == B_PRIMARY || style == B_GOLD || style == B_DANGER || style == B_ON || style == B_DEM || style == B_REP) {
            g.setColor(new Color(255, 255, 255, 40));
            g.draw(new Line2D.Double(x + 6, y + 1.5, x + w - 6, y + 1.5));
        }
        if (edge.getAlpha() > 0) {
            g.setColor(edge);
            g.setStroke(new BasicStroke(hov ? 1.4f : 1f));
            g.draw(rr);
        }
        if (style == B_TABON) {
            g.setColor(C_ACCENT);
            g.fill(new RoundRectangle2D.Double(x + 8, y + h - 3, w - 16, 3, 3, 3));
        }
        boolean tabStyle = style == B_TAB || style == B_TABON;
        Font f = tabStyle ? F_TB : h >= 40 ? F_H2 : h >= 30 ? F_BB : h >= 22 ? F_SB : F_XSB;
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        String s = label;
        while (fm.stringWidth(s) > w - (tabStyle ? 4 : 10) && s.length() > 3) s = s.substring(0, s.length() - 2);
        g.setColor(txt);
        g.drawString(s, (float) (x + (w - fm.stringWidth(s)) / 2), (float) (y + h / 2 + fm.getAscent() / 2.0 - 2.5));
        Hit hh = hit(x, y, w, h, dis ? null : act, tipText);
        hh.lab = label;
        if (hov && hov != lastHoverState(label)) snd(Sfx.HOVER, 0.35, 1);
        return hov;
    }

    static final int IC_UP = 0, IC_DOWN = 1, IC_CLOSE = 2, IC_LEFT = 3, IC_RIGHT = 4;

    /** A small button with a drawn glyph. */
    boolean iconButton(Graphics2D g, double x, double y, double w, double h, int glyph, Runnable act, String tipText) {
        boolean dis = act == null;
        boolean hov = !dis && hover(x, y, w, h);
        RoundRectangle2D rr = new RoundRectangle2D.Double(x, y, w, h, 6, 6);
        g.setColor(hov ? new Color(255, 255, 255, 30) : new Color(255, 255, 255, dis ? 4 : 12));
        g.fill(rr);
        g.setColor(dis ? C_FAINT : hov ? Color.white : C_DIM);
        double cx = x + w / 2, cy = y + h / 2, r = Math.min(w, h) * 0.26;
        Path2D p = new Path2D.Double();
        switch (glyph) {
            case IC_UP: p.moveTo(cx - r, cy + r * 0.6); p.lineTo(cx, cy - r * 0.6); p.lineTo(cx + r, cy + r * 0.6); p.closePath(); g.fill(p); break;
            case IC_DOWN: p.moveTo(cx - r, cy - r * 0.6); p.lineTo(cx, cy + r * 0.6); p.lineTo(cx + r, cy - r * 0.6); p.closePath(); g.fill(p); break;
            case IC_LEFT: p.moveTo(cx + r * 0.6, cy - r); p.lineTo(cx - r * 0.6, cy); p.lineTo(cx + r * 0.6, cy + r); p.closePath(); g.fill(p); break;
            case IC_RIGHT: p.moveTo(cx - r * 0.6, cy - r); p.lineTo(cx + r * 0.6, cy); p.lineTo(cx - r * 0.6, cy + r); p.closePath(); g.fill(p); break;
            default:
                g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Line2D.Double(cx - r, cy - r, cx + r, cy + r));
                g.draw(new Line2D.Double(cx - r, cy + r, cx + r, cy - r));
                g.setStroke(new BasicStroke(1f));
        }
        Hit hh = hit(x, y, w, h, act, tipText);
        hh.lab = "icon" + glyph;
        return hov;
    }

    String lastHover;

    boolean lastHoverState(String label) {
        boolean same = label.equals(lastHover);
        lastHover = label;
        return same;
    }

    /** A glass panel with an optional small-caps title. */
    void panel(Graphics2D g, double x, double y, double w, double h, String title) {
        RoundRectangle2D rr = new RoundRectangle2D.Double(x, y, w, h, 12, 12);
        g.setPaint(new GradientPaint((float) x, (float) y, new Color(18, 28, 48, 236), (float) x, (float) (y + h), new Color(10, 17, 30, 240)));
        g.fill(rr);
        g.setColor(new Color(110, 150, 220, 46));
        g.setStroke(new BasicStroke(1f));
        g.draw(rr);
        g.setColor(new Color(255, 255, 255, 18));
        g.draw(new Line2D.Double(x + 10, y + 1, x + w - 10, y + 1));
        if (title != null) {
            label(g, title, x + 14, y + 22, C_DIM);
        }
    }

    /** A flat card (for list items); returns hover. */
    boolean card(Graphics2D g, double x, double y, double w, double h, boolean selected, Runnable act, String t) {
        boolean hov = act != null && hover(x, y, w, h);
        RoundRectangle2D rr = new RoundRectangle2D.Double(x, y, w, h, 10, 10);
        g.setColor(selected ? new Color(47, 123, 246, 46) : hov ? new Color(255, 255, 255, 16) : new Color(255, 255, 255, 7));
        g.fill(rr);
        g.setColor(selected ? new Color(124, 196, 255, 200) : hov ? new Color(124, 196, 255, 90) : new Color(255, 255, 255, 18));
        g.setStroke(new BasicStroke(selected ? 1.6f : 1f));
        g.draw(rr);
        if (act != null || t != null) hit(x, y, w, h, act, t);
        return hov;
    }

    /** Letter-spaced small caps label. */
    static void label(Graphics2D g, String s, double x, double y, Color c) {
        g.setFont(F_TB);
        g.setColor(c);
        FontMetrics fm = g.getFontMetrics();
        double cx = x;
        String u = s.toUpperCase();
        for (int i = 0; i < u.length(); i++) {
            String ch = u.substring(i, i + 1);
            g.drawString(ch, (float) cx, (float) y);
            cx += fm.stringWidth(ch) + 1.2;
        }
    }

    static double labelWidth(Graphics2D g, String s) {
        FontMetrics fm = g.getFontMetrics(F_TB);
        double w = 0;
        for (char c : s.toUpperCase().toCharArray()) w += fm.charWidth(c) + 1.2;
        return w;
    }

    /** A rounded chip; returns its width. */
    static double chip(Graphics2D g, String s, double x, double y, Color c, boolean filled) {
        g.setFont(F_TB);
        FontMetrics fm = g.getFontMetrics();
        double w = fm.stringWidth(s) + 12;
        RoundRectangle2D rr = new RoundRectangle2D.Double(x, y, w, 16, 16, 16);
        g.setColor(filled ? c : alpha(c, 0.16));
        g.fill(rr);
        g.setColor(alpha(c, filled ? 1 : 0.7));
        g.draw(rr);
        g.setColor(filled ? Color.white : shade(c, 1.35));
        g.drawString(s, (float) (x + 6), (float) (y + 11.5));
        return w;
    }

    static void text(Graphics2D g, String s, double x, double y, Font f, Color c) {
        g.setFont(f);
        g.setColor(c);
        g.drawString(s, (float) x, (float) y);
    }

    static void textR(Graphics2D g, String s, double x, double y, Font f, Color c) {
        g.setFont(f);
        g.setColor(c);
        g.drawString(s, (float) (x - g.getFontMetrics().stringWidth(s)), (float) y);
    }

    static void textC(Graphics2D g, String s, double x, double y, Font f, Color c) {
        g.setFont(f);
        g.setColor(c);
        g.drawString(s, (float) (x - g.getFontMetrics().stringWidth(s) / 2.0), (float) y);
    }

    /** Text that is shortened with an ellipsis to fit. */
    static void textFit(Graphics2D g, String s, double x, double y, double w, Font f, Color c) {
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        String t = s;
        if (fm.stringWidth(t) > w) {
            while (t.length() > 1 && fm.stringWidth(t + "...") > w) t = t.substring(0, t.length() - 1);
            t = t + "...";
        }
        g.setColor(c);
        g.drawString(t, (float) x, (float) y);
    }

    static double strW(Graphics2D g, String s, Font f) { return g.getFontMetrics(f).stringWidth(s); }

    /** Draws word-wrapped text; returns the height used. */
    static double wrap(Graphics2D g, String s, double x, double y, double w, Font f, Color c) {
        g.setFont(f);
        g.setColor(c);
        FontMetrics fm = g.getFontMetrics();
        double lh = fm.getHeight();
        double cy = y;
        for (String para : s.split("\n")) {
            String[] words = para.split(" ");
            StringBuilder line = new StringBuilder();
            for (String wd : words) {
                String t = line.length() == 0 ? wd : line + " " + wd;
                if (fm.stringWidth(t) > w && line.length() > 0) {
                    g.drawString(line.toString(), (float) x, (float) cy);
                    cy += lh;
                    line = new StringBuilder(wd);
                } else line = new StringBuilder(t);
            }
            g.drawString(line.toString(), (float) x, (float) cy);
            cy += lh;
        }
        return cy - y;
    }

    static double wrapHeight(Graphics2D g, String s, double w, Font f) {
        FontMetrics fm = g.getFontMetrics(f);
        double lh = fm.getHeight();
        int lines = 0;
        for (String para : s.split("\n")) {
            String[] words = para.split(" ");
            StringBuilder line = new StringBuilder();
            lines++;
            for (String wd : words) {
                String t = line.length() == 0 ? wd : line + " " + wd;
                if (fm.stringWidth(t) > w && line.length() > 0) { lines++; line = new StringBuilder(wd); } else line = new StringBuilder(t);
            }
        }
        return lines * lh;
    }

    static void bar(Graphics2D g, double x, double y, double w, double h, double frac, Color c) {
        RoundRectangle2D bg = new RoundRectangle2D.Double(x, y, w, h, h, h);
        g.setColor(new Color(255, 255, 255, 16));
        g.fill(bg);
        double f = clamp(frac, 0, 1);
        if (f > 0) {
            g.setPaint(new GradientPaint((float) x, (float) y, shade(c, 1.15), (float) (x + w), (float) y, c));
            g.fill(new RoundRectangle2D.Double(x, y, Math.max(h, w * f), h, h, h));
        }
    }

    /** Signed bar around a centre line. */
    static void sbar(Graphics2D g, double x, double y, double w, double h, double v, Color neg, Color pos) {
        g.setColor(new Color(255, 255, 255, 14));
        g.fill(new RoundRectangle2D.Double(x, y, w, h, h, h));
        double cx = x + w / 2;
        double len = w / 2 * clamp(Math.abs(v), 0, 1);
        g.setColor(v < 0 ? neg : pos);
        g.fill(new Rectangle2D.Double(v < 0 ? cx - len : cx, y, len, h));
        g.setColor(new Color(255, 255, 255, 90));
        g.draw(new Line2D.Double(cx, y - 2, cx, y + h + 2));
    }

    /** A horizontal slider; value in [0,1]. */
    void slider(Graphics2D g, double x, double y, double w, double v, Color c, DFn set, String t) { slider(g, x, y, w, v, c, set, t, true); }

    void slider(Graphics2D g, double x, double y, double w, double v, Color c, DFn set, String t, boolean fill) {
        double h = 6;
        g.setColor(new Color(255, 255, 255, 22));
        g.fill(new RoundRectangle2D.Double(x, y - h / 2, w, h, h, h));
        g.setColor(c);
        if (fill) g.fill(new RoundRectangle2D.Double(x, y - h / 2, w * clamp(v, 0, 1), h, h, h));
        double kx = x + w * clamp(v, 0, 1);
        boolean hov = hover(x - 8, y - 10, w + 16, 20);
        g.setColor(Color.white);
        g.fill(new Ellipse2D.Double(kx - (hov ? 8 : 7), y - (hov ? 8 : 7), hov ? 16 : 14, hov ? 16 : 14));
        g.setColor(c);
        g.fill(new Ellipse2D.Double(kx - 4, y - 4, 8, 8));
        if (set != null) dragRegion(x - 8, y - 11, w + 16, 22, x, w, set, t);
    }

    /** A -/+ stepper; returns the width used. */
    double stepper(Graphics2D g, double x, double y, String value, Runnable minus, Runnable plus, String t, double vw) {
        button(g, x, y, 24, 24, "-", B_NORM, minus, t);
        g.setColor(new Color(0, 0, 0, 70));
        g.fill(new RoundRectangle2D.Double(x + 27, y, vw, 24, 6, 6));
        textC(g, value, x + 27 + vw / 2, y + 16.5, F_SB, C_TEXT);
        button(g, x + 30 + vw, y, 24, 24, "+", B_NORM, plus, t);
        return 54 + vw;
    }

    /** A pill toggle. */
    void toggle(Graphics2D g, double x, double y, boolean on, Runnable act, String t) {
        double w = 36, h = 20;
        boolean hov = hover(x, y, w, h);
        g.setColor(on ? rgb(0x2f9e5f) : new Color(255, 255, 255, hov ? 40 : 26));
        g.fill(new RoundRectangle2D.Double(x, y, w, h, h, h));
        double kx = on ? x + w - h + 2 : x + 2;
        g.setColor(Color.white);
        g.fill(new Ellipse2D.Double(kx, y + 2, h - 4, h - 4));
        hit(x, y, w, h, act, t);
    }

    /** A row of tabs; returns nothing, calls pick on click. */
    void tabs(Graphics2D g, double x, double y, double w, double h, String[] names, int cur, IFn pick) {
        double tw = w / names.length;
        for (int i = 0; i < names.length; i++) {
            final int k = i;
            button(g, x + i * tw, y, tw - 2, h, names[i], i == cur ? B_TABON : B_TAB, () -> pick.on(k));
        }
        g.setColor(new Color(255, 255, 255, 20));
        g.draw(new Line2D.Double(x, y + h, x + w, y + h));
    }

    /** A segmented control. */
    void segmented(Graphics2D g, double x, double y, double w, double h, String[] names, int cur, IFn pick, String[] tips) {
        RoundRectangle2D rr = new RoundRectangle2D.Double(x, y, w, h, 10, 10);
        g.setColor(new Color(0, 0, 0, 80));
        g.fill(rr);
        g.setColor(new Color(255, 255, 255, 22));
        g.draw(rr);
        double tw = w / names.length;
        for (int i = 0; i < names.length; i++) {
            final int k = i;
            double bx = x + i * tw;
            boolean on = i == cur, hov = hover(bx, y, tw, h);
            if (on) {
                g.setPaint(new GradientPaint((float) bx, (float) y, rgb(0x2f6fe0), (float) bx, (float) (y + h), rgb(0x1d4fb0)));
                g.fill(new RoundRectangle2D.Double(bx + 2, y + 2, tw - 4, h - 4, 8, 8));
            } else if (hov) {
                g.setColor(new Color(255, 255, 255, 16));
                g.fill(new RoundRectangle2D.Double(bx + 2, y + 2, tw - 4, h - 4, 8, 8));
            }
            g.setFont(h >= 30 ? F_SB : F_XSB);
            FontMetrics fm = g.getFontMetrics();
            String s = names[i];
            while (fm.stringWidth(s) > tw - 8 && s.length() > 3) s = s.substring(0, s.length() - 2);
            g.setColor(on ? Color.white : hov ? C_TEXT : C_DIM);
            g.drawString(s, (float) (bx + (tw - fm.stringWidth(s)) / 2), (float) (y + h / 2 + fm.getAscent() / 2.0 - 2));
            Hit sh = hit(bx, y, tw, h, () -> pick.on(k), tips == null ? null : tips[i]);
            sh.lab = "seg:" + names[i];
        }
    }

    /** A text field; click to edit. */
    void textField(Graphics2D g, String id, double x, double y, double w, double h, String value, SFn set) {
        boolean ed = id.equals(editId);
        RoundRectangle2D rr = new RoundRectangle2D.Double(x, y, w, h, 8, 8);
        g.setColor(new Color(0, 0, 0, ed ? 120 : 80));
        g.fill(rr);
        g.setColor(ed ? C_ACCENT : new Color(255, 255, 255, 40));
        g.draw(rr);
        String v = ed ? editBuf.toString() : value;
        text(g, v, x + 10, y + h / 2 + 5, F_B, C_TEXT);
        if (ed && ((int) (tsec * 2)) % 2 == 0) {
            double cx = x + 10 + strW(g, v, F_B) + 1;
            g.setColor(C_ACCENT);
            g.fill(new Rectangle2D.Double(cx, y + 6, 1.6, h - 12));
        }
        hit(x, y, w, h, () -> { editId = id; editBuf.setLength(0); editBuf.append(value); editSet = set; }, "Click to type");
    }

    /** Simple multi-series line chart. */
    static void chart(Graphics2D g, double x, double y, double w, double h, List<double[]> series, Color[] cols, String[] names, double lo, double hi, String fmtY, int n) {
        g.setColor(new Color(0, 0, 0, 70));
        g.fill(new RoundRectangle2D.Double(x, y, w, h, 8, 8));
        g.setStroke(new BasicStroke(1f));
        for (int i = 0; i <= 4; i++) {
            double yy = y + 6 + (h - 12) * i / 4.0;
            g.setColor(new Color(255, 255, 255, i == 2 ? 26 : 12));
            g.draw(new Line2D.Double(x + 4, yy, x + w - 4, yy));
            double v = hi - (hi - lo) * i / 4.0;
            if (fmtY != null) textR(g, String.format(fmtY, v), x - 4, yy + 4, F_MS, C_FAINT);
        }
        for (int s = 0; s < series.size(); s++) {
            double[] d = series.get(s);
            int len = Math.min(d.length, n);
            if (len < 2) continue;
            Path2D.Double p = new Path2D.Double();
            for (int i = 0; i < len; i++) {
                double xx = x + 4 + (w - 8) * i / (double) (Math.max(2, n) - 1);
                double yy = y + 6 + (h - 12) * (1 - (d[i] - lo) / (hi - lo == 0 ? 1 : hi - lo));
                yy = clamp(yy, y + 2, y + h - 2);
                if (i == 0) p.moveTo(xx, yy); else p.lineTo(xx, yy);
            }
            g.setColor(cols[s % cols.length]);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(p);
        }
        g.setStroke(new BasicStroke(1f));
        if (names != null) {
            double lx = x + 8;
            for (int s = 0; s < names.length; s++) {
                g.setColor(cols[s % cols.length]);
                g.fill(new RoundRectangle2D.Double(lx, y + 7, 10, 4, 4, 4));
                text(g, names[s], lx + 14, y + 13, F_T, C_DIM);
                lx += 22 + g.getFontMetrics(F_T).stringWidth(names[s]);
            }
        }
    }

    /** Begins a scrollable region; returns the current scroll offset. */
    double beginScroll(Graphics2D g, String id, double x, double y, double w, double h) {
        double[] s = scrolls.get(id);
        if (s == null) { s = new double[]{x, y, w, h, 0, 0, 0}; scrolls.put(id, s); }
        s[0] = x; s[1] = y; s[2] = w; s[3] = h;
        g.setClip(new Rectangle2D.Double(x, y, w, h));
        return Math.round(s[4]);
    }

    void endScroll(Graphics2D g, String id, double contentH) {
        double[] s = scrolls.get(id);
        g.setClip(0, 0, DW, DH);
        if (s == null) return;
        s[5] = Math.max(0, contentH - s[3]);
        s[6] = clamp(s[6], 0, s[5]);
        s[4] = clamp(s[4], 0, s[5]);
        if (s[5] > 0) {
            double th = Math.max(24, s[3] * s[3] / contentH);
            double ty = s[1] + (s[3] - th) * (s[4] / s[5]);
            g.setColor(new Color(255, 255, 255, 14));
            g.fill(new RoundRectangle2D.Double(s[0] + s[2] - 5, s[1] + 2, 4, s[3] - 4, 4, 4));
            g.setColor(new Color(124, 196, 255, 130));
            g.fill(new RoundRectangle2D.Double(s[0] + s[2] - 5, ty, 4, th, 4, 4));
        }
    }

    void resetScroll(String id) { double[] s = scrolls.get(id); if (s != null) { s[4] = 0; s[6] = 0; } }

    /** Hits outside the visible part of a scroll region are dropped (call after drawing the list). */
    void clipHits(int from, double y0, double y1) {
        for (int i = hits.size() - 1; i >= from; i--) {
            Hit h = hits.get(i);
            if (h.y + h.h < y0 || h.y > y1) hits.remove(i);
            else if (h.y < y0) { h.h -= y0 - h.y; h.y = y0; }
            else if (h.y + h.h > y1) h.h = y1 - h.y;
        }
    }

    // ---- modal ------------------------------------------------------------------------------------------
    void modal(String title, String body, String[] buttons, Runnable[] actions, Color col) {
        Modal m = new Modal();
        m.title = title; m.body = body; m.buttons = buttons; m.actions = actions; m.col = col == null ? C_ACCENT : col;
        if (modal == null) modal = m; else modalQueue.add(m);
        snd(Sfx.OPEN);
    }

    void info(String title, String body) { modal(title, body, new String[]{"OK"}, new Runnable[]{null}, null); }

    void closeModal() { modal = null; }

    void drawModal(Graphics2D g) {
        Modal m = modal;
        g.setColor(new Color(2, 6, 14, 175));
        g.fillRect(0, 0, DW, DH);
        double w = m.w;
        double faceW = m.face != null ? 150 : 0;
        double tw = w - 56 - faceW;
        double th = m.body == null ? 0 : wrapHeight(g, m.body, tw, F_B);
        double oh = 0;
        if (m.stacked && m.buttons != null) for (int i = 0; i < m.buttons.length; i++) oh += Math.max(46, 28 + (m.descs != null && m.descs[i] != null ? wrapHeight(g, m.descs[i], w - 76, F_XS) : 0)) + 8;
        // stacked options always start below the portrait so they never run into the candidate's face
        double faceBottom = m.face != null ? 26 + 150 + 14 : 0;
        double textBottom = 74 + th + m.extraH;
        double optsTop = m.stacked ? Math.max(textBottom + 8, faceBottom) : textBottom;
        double h = Math.min(DH - 60, Math.max(m.face != null ? 250 : 0, optsTop + (m.stacked ? oh + 16 : 64)));
        double x = (DW - w) / 2, y = (DH - h) / 2;
        double pop = easeOut(Math.min(1, (tsec - modalT(m)) * 5));
        y += (1 - pop) * 18;
        RoundRectangle2D rr = new RoundRectangle2D.Double(x, y, w, h, 16, 16);
        g.setColor(new Color(0, 0, 0, 120));
        g.fill(new RoundRectangle2D.Double(x + 4, y + 10, w, h, 16, 16));
        g.setPaint(new GradientPaint((float) x, (float) y, rgb(0x16233b), (float) x, (float) (y + h), rgb(0x0c1424)));
        g.fill(rr);
        g.setColor(alpha(m.col, 0.75));
        g.setStroke(new BasicStroke(1.4f));
        g.draw(rr);
        g.setColor(m.col);
        g.fill(new RoundRectangle2D.Double(x + 24, y, 80, 4, 4, 4));
        double tx = x + 28 + faceW;
        if (m.face != null) Portrait.draw(g, m.face, x + 22, y + 26, 128, 150, tsec, 0, false, true);
        textFit(g, m.title, tx, y + 44, w - 56 - faceW, F_H1, Color.white);
        double cy = y + 74;
        if (m.body != null) cy += wrap(g, m.body, tx, cy, tw, F_B, C_TEXT);
        if (m.painter != null) { m.painter.paint(g, tx, cy, tw, m.extraH); cy += m.extraH; }
        if (m.buttons == null) return;
        if (m.stacked) {
            cy = Math.max(cy + 8, y + faceBottom);
            for (int i = 0; i < m.buttons.length; i++) {
                final int k = i;
                String d = m.descs != null ? m.descs[i] : null;
                double bh = Math.max(46, 28 + (d != null ? wrapHeight(g, d, w - 76, F_XS) : 0));
                boolean hov = card(g, x + 24, cy, w - 48, bh, false, () -> { Runnable r = m.actions[k]; closeModal(); if (r != null) r.run(); }, null);
                if (!hits.isEmpty()) hits.get(hits.size() - 1).lab = m.buttons[i];
                g.setColor(hov ? m.col : alpha(m.col, 0.6));
                g.fill(new RoundRectangle2D.Double(x + 24, cy, 4, bh, 4, 4));
                text(g, m.buttons[i], x + 40, cy + 20, F_BB, hov ? Color.white : C_TEXT);
                if (d != null) wrap(g, d, x + 40, cy + 37, w - 76, F_XS, C_DIM);
                cy += bh + 8;
            }
        } else {
            double bw = Math.max(130, Math.min(200, (w - 48 - 10 * (m.buttons.length - 1)) / m.buttons.length)), bx = x + w - 24 - bw * m.buttons.length - 10 * (m.buttons.length - 1);
            for (int i = 0; i < m.buttons.length; i++) {
                final int k = i;
                button(g, bx + i * (bw + 10), y + h - 52, bw, 34, m.buttons[i], i == m.buttons.length - 1 ? (m.col == C_BAD ? B_DANGER : B_PRIMARY) : B_NORM, () -> {
                    Runnable r = m.actions[k];
                    closeModal();
                    if (r != null) r.run();
                });
            }
        }
    }

    final Map<Modal, Double> modalTimes = new HashMap<Modal, Double>();

    double modalT(Modal m) {
        Double t = modalTimes.get(m);
        if (t == null) { t = tsec; modalTimes.clear(); modalTimes.put(m, t); }
        return t;
    }

    // ---- popup menus -----------------------------------------------------------------------------------------
    Popup openPopup(double x, double y, double w, String title, String[] items, IFn pick) {
        Popup p = new Popup();
        p.x = x; p.y = y; p.w = w; p.title = title; p.items = items; p.pick = pick;
        popup = p;
        snd(Sfx.OPEN, 0.5, 1.3);
        return p;
    }

    void drawPopup(Graphics2D g) {
        Popup p = popup;
        double rowH = 26, head = p.title != null ? 26 : 6;
        double h = head + p.items.length * rowH + 6;
        double x = Math.min(p.x, DW - p.w - 8), y = Math.min(p.y, DH - h - 8);
        y = Math.max(8, y);
        RoundRectangle2D rr = new RoundRectangle2D.Double(x, y, p.w, h, 12, 12);
        g.setColor(new Color(0, 0, 0, 130));
        g.fill(new RoundRectangle2D.Double(x + 3, y + 8, p.w, h, 12, 12));
        g.setColor(rgb(0x121d31));
        g.fill(rr);
        g.setColor(new Color(124, 196, 255, 110));
        g.draw(rr);
        if (p.title != null) label(g, p.title, x + 12, y + 18, C_DIM);
        for (int i = 0; i < p.items.length; i++) {
            final int k = i;
            double ry = y + head + i * rowH;
            boolean en = p.enabled == null || p.enabled[i];
            boolean hov = en && hover(x + 4, ry, p.w - 8, rowH);
            if (hov) {
                g.setColor(new Color(47, 123, 246, 70));
                g.fill(new RoundRectangle2D.Double(x + 4, ry + 1, p.w - 8, rowH - 2, 8, 8));
            }
            if (p.cols != null && p.cols[i] != null) {
                g.setColor(p.cols[i]);
                g.fill(new RoundRectangle2D.Double(x + 12, ry + 9, 8, 8, 3, 3));
            }
            text(g, p.items[i], x + (p.cols != null ? 28 : 12), ry + 17, F_S, en ? (hov ? Color.white : C_TEXT) : C_FAINT);
            if (p.right != null && p.right[i] != null) textR(g, p.right[i], x + p.w - 12, ry + 17, F_XS, en ? C_DIM : C_FAINT);
            Hit ph = hit(x + 4, ry, p.w - 8, rowH, en ? () -> { popup = null; p.pick.on(k); } : null, p.tips != null ? p.tips[i] : null);
            ph.lab = "popup:" + p.items[i];
        }
    }

    // ---- tooltips and toasts ---------------------------------------------------------------------------------------
    void drawTip(Graphics2D g, String t) {
        g.setFont(F_S);
        FontMetrics fm = g.getFontMetrics();
        double maxW = 340;
        List<String> wrapped = new ArrayList<String>();
        for (String l : t.split("\n")) {
            if (fm.stringWidth(l) <= maxW) { wrapped.add(l); continue; }
            StringBuilder cur = new StringBuilder();
            for (String wd : l.split(" ")) {
                if (fm.stringWidth(cur + " " + wd) > maxW && cur.length() > 0) { wrapped.add(cur.toString()); cur = new StringBuilder(wd); }
                else cur.append(cur.length() > 0 ? " " : "").append(wd);
            }
            wrapped.add(cur.toString());
        }
        double w = 0;
        for (String l : wrapped) w = Math.max(w, fm.stringWidth(l));
        double h = wrapped.size() * fm.getHeight() + 12;
        double x = mx + 16, y = my + 18;
        if (x + w + 20 > DW) x = mx - w - 24;
        if (y + h > DH) y = my - h - 8;
        RoundRectangle2D rr = new RoundRectangle2D.Double(x, y, w + 18, h, 10, 10);
        g.setColor(new Color(4, 9, 18, 242));
        g.fill(rr);
        g.setColor(new Color(124, 196, 255, 110));
        g.draw(rr);
        double cy = y + fm.getAscent() + 6;
        boolean first = true;
        for (String l : wrapped) { text(g, l, x + 9, cy, first && wrapped.size() > 1 && t.indexOf('\n') > 0 && l.equals(t.split("\n")[0]) ? F_SB : F_S, C_TEXT); cy += fm.getHeight(); first = false; }
    }

    void drawToasts(Graphics2D g) {
        boolean hq = screen == SC_HQ;
        double y = hq ? Hq.MAPY + Hq.MAPH - 44 : DH - 150;
        for (int i = toasts.size() - 1; i >= 0; i--) {
            Toast t = toasts.get(i);
            double age = tsec - t.t0;
            if (age > 5) { toasts.remove(i); continue; }
            double a = Math.min(1, Math.min(age * 5, (5 - age) * 2));
            double w = Math.min(hq ? 600 : 460, strW(g, t.text, F_S) + 36);
            double x = hq ? Hq.MX + (Hq.MW - w) / 2 : DW - w - 20 + (1 - easeOut(Math.min(1, age * 4))) * 40;
            if (hq) y -= 0;
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) clamp(a, 0, 1)));
            RoundRectangle2D rr = new RoundRectangle2D.Double(x, y, w, 34, 10, 10);
            g.setColor(new Color(8, 14, 26, 235));
            g.fill(rr);
            g.setColor(alpha(t.col, 0.7));
            g.draw(rr);
            g.setColor(t.col);
            g.fill(new RoundRectangle2D.Double(x + 8, y + 10, 4, 14, 4, 4));
            textFit(g, t.text, x + 20, y + 22, w - 28, F_S, C_TEXT);
            g.setComposite(AlphaComposite.SrcOver);
            y -= 40;
        }
    }

    // ---- backgrounds ------------------------------------------------------------------------------------------------
    static BufferedImage bgCache;

    /** Deep navy war-room backdrop with a faint grid and vignette (cached). */
    void backdrop(Graphics2D g) {
        if (bgCache == null) {
            BufferedImage img = new BufferedImage(DW, DH, BufferedImage.TYPE_INT_RGB);
            Graphics2D b = img.createGraphics();
            b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            b.setPaint(new GradientPaint(0, 0, rgb(0x081120), 0, DH, rgb(0x04080f)));
            b.fillRect(0, 0, DW, DH);
            b.setPaint(new RadialGradientPaint(new Point2D.Double(DW * 0.5, DH * 0.42), DW * 0.6f, new float[]{0f, 1f},
                    new Color[]{new Color(40, 80, 160, 60), new Color(0, 0, 0, 0)}));
            b.fillRect(0, 0, DW, DH);
            b.setColor(new Color(120, 160, 230, 9));
            for (int x = 0; x < DW; x += 40) b.drawLine(x, 0, x, DH);
            for (int y = 0; y < DH; y += 40) b.drawLine(0, y, DW, y);
            b.setPaint(new RadialGradientPaint(new Point2D.Double(DW / 2.0, DH / 2.0), DW * 0.75f, new float[]{0.55f, 1f},
                    new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 150)}));
            b.fillRect(0, 0, DW, DH);
            b.dispose();
            bgCache = img;
        }
        g.drawImage(bgCache, 0, 0, null);
    }

    /** Renders the current screen into an image (used for testing). */
    BufferedImage snapshot(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        winW = w; winH = h;
        render(g, w, h);
        g.dispose();
        return img;
    }

    // =====================================================================
    //  21.  PORTRAITS  -  stylised, animated caricatures drawn from each candidate's Look
    // =====================================================================
    static final class Portrait {
        static final Color[] SKIN = {rgb(0xf4d7c3), rgb(0xebc3a3), rgb(0xd9a882), rgb(0xc58c63), rgb(0xa76d4a), rgb(0x7e4f34), rgb(0x5f3a26), rgb(0x45281a)};
        static final Color[] HAIR = {rgb(0x17120f), rgb(0x3a281d), rgb(0x5b3d29), rgb(0x7b5a3b), rgb(0xa58358), rgb(0xd8b46c), rgb(0x9c9a98), rgb(0xe8e6e2)};
        static final Color[] SUIT = {rgb(0x1d2a4a), rgb(0x34383f), rgb(0x16171b), rgb(0x9cbbe0), rgb(0x4a4038), rgb(0x2b3b5c)};
        static final Color[] TIE = {rgb(0x2f6fe0), rgb(0xc8323a), rgb(0x203a72), rgb(0xd4a52a), rgb(0x6a3fa0), rgb(0x2f8f5a), rgb(0x8a1f2a), rgb(0x5aa0d8), rgb(0x8a8f99), null};
        static final Color[] BLOUSE = {rgb(0xf2ece2), rgb(0xc8323a), rgb(0x2f6fe0), rgb(0x1f7a6a), rgb(0x6a3fa0), rgb(0xe7b9a3), rgb(0x16171b), rgb(0xd4a52a), rgb(0x9cbbe0), rgb(0xf2ece2)};
        static final Color[] IRIS = {rgb(0x4a2f1e), rgb(0x3b6ea8), rgb(0x5e7a3d), rgb(0x2a1d14)};
        static final Map<String, BufferedImage> cache = new HashMap<String, BufferedImage>();

        static Color partyBg(int p) {
            switch (p) {
                case DEM: return rgb(0x1f4fae);
                case REP: return rgb(0xb12a33);
                case GRN: return rgb(0x2a8c4a);
                case LBT: return rgb(0xb88a1d);
                case FWD: return rgb(0x6a3fbf);
                case IND: return rgb(0x9a5a0b);
                default: return rgb(0x14899a);
            }
        }

        /** Draws a portrait. still = cached, unanimated; framed = with party card background. talk 0..1 animates speech. */
        static void draw(Graphics2D g, Cand c, double x, double y, double w, double h, double t, double talk, boolean still, boolean framed) {
            if (c == null || c.look == null) return;
            if (still) {
                int pw = (int) Math.max(8, Math.round(w * 2)), ph = (int) Math.max(8, Math.round(h * 2));
                String key = c.id + "|" + c.look.hashCode() + "|" + pw + "x" + ph + (framed ? "f" : "");
                BufferedImage img = cache.get(key);
                if (img == null) {
                    if (cache.size() > 400) cache.clear();
                    img = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D b = img.createGraphics();
                    hints(b);
                    paint(b, c, 0, 0, pw, ph, 0.7, 0, framed, true);
                    b.dispose();
                    cache.put(key, img);
                }
                Object old = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(img, (int) Math.round(x), (int) Math.round(y), (int) Math.round(w), (int) Math.round(h), null);
                if (old != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, old);
                return;
            }
            paint(g, c, x, y, w, h, t, talk, framed, false);
        }

        static void hints(Graphics2D b) {
            b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            b.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            b.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        }

        /** A round head-and-shoulders avatar. */
        static void avatar(Graphics2D g, Cand c, double cx, double cy, double r, Color ring) {
            Shape old = g.getClip();
            Ellipse2D circ = new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r);
            g.setColor(c == null ? C_STATE : shade(partyBg(c.party), 0.55));
            g.fill(circ);
            if (c != null) {
                Area clip = new Area(circ);
                if (old != null) clip.intersect(new Area(old));
                g.setClip(clip);
                double w = r * 2.5, h = w * 1.2;
                draw(g, c, cx - w / 2, cy - h * 0.36, w, h, 0, 0, true, false);
                g.setClip(old);
            }
            if (ring != null) {
                g.setColor(ring);
                g.setStroke(new BasicStroke(2f));
                g.draw(circ);
                g.setStroke(new BasicStroke(1f));
            }
        }

        static void paint(Graphics2D g, Cand c, double x, double y, double w, double h, double t, double talk, boolean framed, boolean still) {
            AffineTransform saved = g.getTransform();
            Shape oldClip = g.getClip();
            Look L = c.look;
            double ph = (Math.abs(c.id.hashCode()) % 1000) / 159.0;           // per-candidate animation phase
            g.translate(x, y);
            g.scale(w / 200.0, h / 240.0);
            RoundRectangle2D card = new RoundRectangle2D.Double(0, 0, 200, 240, 22, 22);
            if (framed) {
                Color pc = partyBg(c.party);
                g.setPaint(new GradientPaint(0, 0, shade(pc, 0.95), 0, 240, shade(pc, 0.32)));
                g.fill(card);
                g.clip(card);
                // stripes and stars
                g.setColor(new Color(255, 255, 255, 14));
                for (int i = -4; i < 12; i++) g.fill(new Rectangle2D.Double(i * 28, 0, 9, 260));
                g.setColor(new Color(255, 255, 255, 30));
                for (int i = 0; i < 9; i++) star(g, 18 + (i % 3) * 22 + (i / 3 % 2) * 11, 18 + (i / 3) * 18, 3.2);
                g.setPaint(new RadialGradientPaint(new Point2D.Double(100, 92), 120f, new float[]{0f, 1f},
                        new Color[]{new Color(255, 255, 255, 70), new Color(255, 255, 255, 0)}));
                g.fill(new Rectangle2D.Double(0, 0, 200, 240));
            }
            // animation
            double breath = still ? 0 : Math.sin(t * 1.7 + ph) * 1.3;
            double tilt = still ? 0 : Math.sin(t * 0.45 + ph) * 0.022 + Math.sin(t * 1.3 + ph * 2) * 0.006 + talk * Math.sin(t * 3.1) * 0.02;
            double blinkPhase = (t + ph * 3) % (3.6 + (ph % 1.5));
            double open = still ? 1 : blinkPhase < 0.13 ? Math.abs(blinkPhase - 0.065) / 0.065 : 1;
            double gazeX = still ? 0 : Math.sin(t * 0.37 + ph) * 1.4 + Math.sin(t * 1.9 + ph) * 0.4;
            double gazeY = still ? 0 : Math.sin(t * 0.29 + ph * 1.7) * 0.6;
            double mouthOpen = 0;
            if (talk > 0 && !still) mouthOpen = talk * Math.max(0, 0.55 + 0.45 * Math.sin(t * 15.0 + ph)) * (0.55 + 0.45 * Math.abs(Math.sin(t * 6.3 + ph)));
            Color skin = SKIN[clampi(L.skin, 0, SKIN.length - 1)];
            Color hair = mix(HAIR[clampi(L.hair, 0, HAIR.length - 1)], rgb(0xc9c7c4), L.gray);
            double cx = 100, cy = 100 + breath * 0.5;
            double hw = 43 * L.faceW, hh = 55;
            // ---- back hair (long styles)
            AffineTransform preHair = g.getTransform();
            g.rotate(tilt, 100, 190);
            g.translate(100, 197);
            g.scale(1.13, 1.13);
            g.translate(-100, -192);
            if (L.style == 6 || L.style == 7 || L.style == 8 || L.style == 9) backHair(g, L, hair, cx, cy, hw, hh);
            g.setTransform(preHair);
            // ---- body
            body(g, c, L, breath);
            g.rotate(tilt, 100, 190);
            g.translate(100, 197);
            g.scale(1.13, 1.13);
            g.translate(-100, -192);
            // ---- neck
            Path2D neck = new Path2D.Double();
            neck.moveTo(cx - 19, cy + hh * 0.55);
            neck.lineTo(cx - 21, 192 + breath);
            neck.quadTo(cx, 200 + breath, cx + 21, 192 + breath);
            neck.lineTo(cx + 19, cy + hh * 0.55);
            neck.closePath();
            g.setColor(shade(skin, 0.86));
            g.fill(neck);
            g.setPaint(new GradientPaint((float) cx, (float) (cy + hh * 0.8), new Color(0, 0, 0, 70), (float) cx, (float) (cy + hh * 1.25), new Color(0, 0, 0, 0)));
            g.fill(neck);
            // ---- ears
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                Ellipse2D ear = new Ellipse2D.Double(cx + sgn * (hw + 1) - 7, cy - 4, 14, 25);
                g.setColor(shade(skin, 0.93));
                g.fill(ear);
                g.setColor(shade(skin, 0.72));
                g.setStroke(new BasicStroke(1.3f));
                g.draw(new Arc2D.Double(cx + sgn * (hw + 1) - 4, cy + 1, 8, 15, sgn < 0 ? 90 : -90, 180, Arc2D.OPEN));
                if (L.earrings) {
                    g.setColor(rgb(0xf1d27a));
                    g.fill(new Ellipse2D.Double(cx + sgn * (hw + 1) - 2.5, cy + 19, 5, 5));
                    g.setColor(new Color(255, 255, 255, 160));
                    g.fill(new Ellipse2D.Double(cx + sgn * (hw + 1) - 1.5, cy + 19.5, 2, 2));
                }
            }
            // ---- face
            double jawW = hw * (0.52 + 0.34 * L.jaw), chinW = 9 + 11 * L.jaw;
            Path2D face = new Path2D.Double();
            face.moveTo(cx - hw, cy - 4);
            face.curveTo(cx - hw, cy - hh * 1.36, cx + hw, cy - hh * 1.36, cx + hw, cy - 4);
            face.curveTo(cx + hw * 1.01, cy + hh * 0.42, cx + jawW + 4, cy + hh * 0.84, cx + chinW, cy + hh * 0.98);
            face.curveTo(cx + chinW * 0.45, cy + hh * 1.05, cx - chinW * 0.45, cy + hh * 1.05, cx - chinW, cy + hh * 0.98);
            face.curveTo(cx - jawW - 4, cy + hh * 0.84, cx - hw * 1.01, cy + hh * 0.42, cx - hw, cy - 4);
            face.closePath();
            g.setPaint(new RadialGradientPaint(new Point2D.Double(cx - hw * 0.25, cy - hh * 0.25), (float) (hw * 1.9),
                    new float[]{0f, 0.62f, 1f}, new Color[]{shade(skin, 1.07), skin, shade(skin, 0.80)}));
            g.fill(face);
            Shape faceClip = face;
            Shape prevClip = g.getClip();
            g.clip(faceClip);
            // cheek warmth and jaw shading
            Color blush = mix(skin, rgb(0xd0584e), 0.45);
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                g.setPaint(new RadialGradientPaint(new Point2D.Double(cx + sgn * hw * 0.55, cy + 22), 18f, new float[]{0f, 1f},
                        new Color[]{alpha(blush, L.female ? 0.30 : 0.16), alpha(blush, 0)}));
                g.fill(new Rectangle2D.Double(cx - hw, cy, hw * 2, 50));
            }
            g.setPaint(new GradientPaint((float) cx, (float) (cy + hh * 0.5), new Color(0, 0, 0, 0), (float) cx, (float) (cy + hh), new Color(0, 0, 0, 34)));
            g.fill(new Rectangle2D.Double(cx - hw, cy + hh * 0.5, hw * 2, hh * 0.6));
            // stubble / shaved-head shadow inside the face
            if (L.facial == 3 || L.facial == 1 || L.facial == 2) {
                g.setColor(alpha(shade(hair, 0.7), L.facial == 3 ? 0.20 : 0.10));
                g.fill(new Ellipse2D.Double(cx - jawW - 6, cy + 18, (jawW + 6) * 2, hh));
            }
            g.setClip(prevClip);
            g.setColor(shade(skin, 0.66));
            g.setStroke(new BasicStroke(1.4f));
            g.draw(face);
            // ---- age lines
            double age = L.age;
            if (age > 0.5) {
                double a = clamp((age - 0.5) * 1.3, 0, 0.55);
                g.setColor(alpha(shade(skin, 0.55), a * 0.55));
                g.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for (int i = 0; i < (age > 0.7 ? 3 : 2); i++) g.draw(new Arc2D.Double(cx - 18 + i * 2, cy - 34 - i * 5, 36 - i * 4, 8, 20, 140, Arc2D.OPEN));
                for (int sgn = -1; sgn <= 1; sgn += 2) {
                    double ex = cx + sgn * hw * 0.42;
                    g.draw(new Line2D.Double(ex + sgn * 12, cy + 4, ex + sgn * 16, cy + 1));
                    g.draw(new Line2D.Double(ex + sgn * 12, cy + 7, ex + sgn * 16, cy + 8));
                    Path2D nl = new Path2D.Double();
                    nl.moveTo(cx + sgn * 9, cy + 25);
                    nl.quadTo(cx + sgn * 15, cy + 33, cx + sgn * 14, cy + 42);
                    g.setColor(alpha(shade(skin, 0.55), a * 0.7));
                    g.draw(nl);
                }
            }
            // ---- eyes
            double eyeY = cy + 3 + gazeY * 0.2, eyeDX = hw * 0.41, ew = 9.0 * Math.sqrt(L.faceW), eh = 4.8 * open;
            Color iris = IRIS[(L.skin <= 1 && (L.hair >= 3 && L.hair <= 5)) ? 1 : (L.skin <= 1 && (c.id.hashCode() & 3) == 1) ? 2 : (L.skin >= 4 ? 3 : 0)];
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                double ex = cx + sgn * eyeDX;
                // socket shadow
                g.setPaint(new RadialGradientPaint(new Point2D.Double(ex, eyeY - 2), 15f, new float[]{0f, 1f},
                        new Color[]{alpha(shade(skin, 0.7), 0.35), alpha(shade(skin, 0.7), 0)}));
                g.fill(new Ellipse2D.Double(ex - 15, eyeY - 14, 30, 24));
                Path2D eye = new Path2D.Double();
                eye.moveTo(ex - ew, eyeY);
                eye.quadTo(ex - ew * 0.2, eyeY - eh * 1.55 - 0.2, ex + ew, eyeY - 0.5);
                eye.quadTo(ex + ew * 0.1, eyeY + eh * 1.15, ex - ew, eyeY);
                eye.closePath();
                if (eh > 0.6) {
                    g.setColor(rgb(0xf7f2ec));
                    g.fill(eye);
                    Shape pc = g.getClip();
                    g.clip(eye);
                    double ix = ex + gazeX, iy = eyeY - 0.6;
                    g.setColor(iris);
                    g.fill(new Ellipse2D.Double(ix - 4.3, iy - 4.3, 8.6, 8.6));
                    g.setColor(rgb(0x0d0907));
                    g.fill(new Ellipse2D.Double(ix - 2.1, iy - 2.1, 4.2, 4.2));
                    g.setColor(new Color(255, 255, 255, 220));
                    g.fill(new Ellipse2D.Double(ix + 0.6, iy - 2.6, 1.9, 1.9));
                    g.setColor(new Color(0, 0, 0, 40));
                    g.fill(new Rectangle2D.Double(ex - ew, eyeY - 8, ew * 2, 3.2));
                    g.setClip(pc);
                }
                g.setColor(rgb(0x2a1c16));
                g.setStroke(new BasicStroke(L.female ? 2.2f : 1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Path2D lid = new Path2D.Double();
                lid.moveTo(ex - ew - 0.5, eyeY + 0.3);
                lid.quadTo(ex - ew * 0.2, eyeY - eh * 1.55 - 0.2, ex + ew + (L.female ? 1.5 : 0), eyeY - (L.female ? 1.6 : 0.5));
                g.draw(lid);
                g.setColor(alpha(shade(skin, 0.5), 0.6));
                g.setStroke(new BasicStroke(0.9f));
                g.draw(new Arc2D.Double(ex - ew * 0.8, eyeY - 5 - eh * 0.6, ew * 1.6, 8, 20, 140, Arc2D.OPEN));
                if (age > 0.5) {
                    g.setColor(alpha(shade(skin, 0.6), (age - 0.5) * 0.9));
                    g.draw(new Arc2D.Double(ex - ew * 0.7, eyeY + 1, ew * 1.4, 6, 200, 140, Arc2D.OPEN));
                }
            }
            // ---- brows
            Color browC = L.style == 0 && L.hair <= 1 ? rgb(0x241a14) : shade(hair, L.hair >= 6 ? 0.8 : 0.85);
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                double ex = cx + sgn * eyeDX;
                double by = eyeY - 11 - L.brow * 3 + (talk > 0 && !still ? -Math.max(0, Math.sin(t * 2.3 + ph)) * 1.6 * talk : 0);
                Path2D b = new Path2D.Double();
                b.moveTo(ex - sgn * (ew - 1), by + 1.5);
                b.quadTo(ex + sgn * 1, by - 3.5, ex + sgn * (ew + 3), by + 1);
                g.setColor(browC);
                g.setStroke(new BasicStroke(L.female ? 2.4f : 3.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(b);
            }
            // ---- nose
            g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(alpha(shade(skin, 0.62), 0.75));
            Path2D nose = new Path2D.Double();
            nose.moveTo(cx + 2.5, eyeY + 3);
            nose.quadTo(cx + 6, cy + 16, cx + 6.5, cy + 23);
            g.draw(nose);
            Path2D tip = new Path2D.Double();
            tip.moveTo(cx - 7, cy + 24);
            tip.quadTo(cx - 4, cy + 28.5, cx, cy + 27.5);
            tip.quadTo(cx + 4, cy + 28.5, cx + 7, cy + 24);
            g.setColor(alpha(shade(skin, 0.55), 0.85));
            g.draw(tip);
            g.setColor(alpha(shade(skin, 1.25), 0.6));
            g.fill(new Ellipse2D.Double(cx - 2.5, cy + 19, 4, 3));
            // ---- facial hair under the mouth
            Color fh = shade(hair, 0.9);
            if (L.facial == 1 || L.facial == 2) {
                Path2D beard = new Path2D.Double();
                double bt = L.facial == 1 ? 1.0 : 0.6;
                beard.moveTo(cx - hw + 2, cy + 6);
                beard.curveTo(cx - hw * 0.98, cy + hh * 0.5, cx - jawW - 6, cy + hh * (0.9 + 0.12 * bt), cx, cy + hh * (1.02 + 0.12 * bt));
                beard.curveTo(cx + jawW + 6, cy + hh * (0.9 + 0.12 * bt), cx + hw * 0.98, cy + hh * 0.5, cx + hw - 2, cy + 6);
                beard.lineTo(cx + hw - 6, cy + 10);
                beard.curveTo(cx + hw * 0.7, cy + 30, cx + 16, cy + 30, cx + 15, cy + 36);
                beard.quadTo(cx, cy + 32, cx - 15, cy + 36);
                beard.curveTo(cx - 16, cy + 30, cx - hw * 0.7, cy + 30, cx - hw + 6, cy + 10);
                beard.closePath();
                g.setColor(alpha(fh, 0.92));
                g.fill(beard);
                g.setColor(alpha(shade(fh, 1.3), 0.25));
                for (int i = 0; i < 18; i++) {
                    double a = i / 17.0;
                    double bx = cx - jawW + a * jawW * 2, by = cy + hh * 0.72 + Math.sin(a * Math.PI) * 14;
                    g.draw(new Line2D.Double(bx, by, bx + 1, by + 4));
                }
            }
            if (L.facial == 5) {
                Path2D goatee = new Path2D.Double();
                goatee.moveTo(cx - 13, cy + 44);
                goatee.quadTo(cx, cy + hh * 1.12, cx + 13, cy + 44);
                goatee.quadTo(cx, cy + 50, cx - 13, cy + 44);
                g.setColor(alpha(fh, 0.92));
                g.fill(goatee);
            }
            // ---- mouth
            double my = cy + 38, mw = 13 + L.smile * 3 + (L.female ? 0 : 1);
            double up = (L.smile - 0.45) * 7;
            Color lipC = L.lipstick ? rgb(0xb8324a) : mix(shade(skin, 0.78), rgb(0xa8505a), 0.45);
            if (mouthOpen > 0.05 || L.smile > 0.72) {
                double oh = Math.max(mouthOpen * 9, L.smile > 0.72 ? 3.6 : 0);
                Path2D m = new Path2D.Double();
                m.moveTo(cx - mw, my - up);
                m.quadTo(cx, my + 2 + oh * 1.6 + up * 0.4, cx + mw, my - up);
                m.quadTo(cx, my + 1.0, cx - mw, my - up);
                m.closePath();
                g.setColor(rgb(0x4a1d1f));
                g.fill(m);
                Shape pc = g.getClip();
                g.clip(m);
                g.setColor(rgb(0xf4efe6));
                g.fill(new Rectangle2D.Double(cx - mw, my - up - 1, mw * 2, 3.2 + oh * 0.25));
                g.setClip(pc);
                g.setColor(lipC);
                g.setStroke(new BasicStroke(L.lipstick ? 2.4f : 1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(m);
            } else {
                Path2D m = new Path2D.Double();
                m.moveTo(cx - mw, my - up);
                m.quadTo(cx, my + 2.5 + up * 0.5, cx + mw, my - up);
                g.setColor(alpha(shade(lipC, 0.75), 0.95));
                g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(m);
                if (L.lipstick || L.female) {
                    Path2D lower = new Path2D.Double();
                    lower.moveTo(cx - mw * 0.75, my + 1);
                    lower.quadTo(cx, my + 7, cx + mw * 0.75, my + 1);
                    g.setColor(alpha(lipC, L.lipstick ? 0.85 : 0.45));
                    g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.draw(lower);
                }
                g.setColor(alpha(shade(skin, 0.6), 0.4));
                g.setStroke(new BasicStroke(1f));
                g.draw(new Arc2D.Double(cx - 5, my + 7, 10, 4, 200, 140, Arc2D.OPEN));
            }
            if (L.facial == 4 || L.facial == 1 || L.facial == 2) {
                Path2D mus = new Path2D.Double();
                mus.moveTo(cx - mw - 2, my - up + 1);
                mus.quadTo(cx - mw * 0.6, my - 9, cx, my - 6.5);
                mus.quadTo(cx + mw * 0.6, my - 9, cx + mw + 2, my - up + 1);
                mus.quadTo(cx, my - 2.5, cx - mw - 2, my - up + 1);
                g.setColor(alpha(fh, 0.95));
                g.fill(mus);
            }
            // ---- glasses
            if (L.glasses > 0) {
                g.setColor(L.glasses == 2 ? rgb(0x161616) : rgb(0x4b4b52));
                g.setStroke(new BasicStroke(L.glasses == 2 ? 2.6f : 1.6f));
                for (int sgn = -1; sgn <= 1; sgn += 2) {
                    double ex = cx + sgn * eyeDX;
                    RoundRectangle2D lens = new RoundRectangle2D.Double(ex - ew - 4, eyeY - 8, (ew + 4) * 2, 15, 8, 8);
                    g.setColor(new Color(200, 230, 255, 26));
                    g.fill(lens);
                    g.setColor(L.glasses == 2 ? rgb(0x161616) : rgb(0x4b4b52));
                    g.draw(lens);
                    g.draw(new Line2D.Double(ex + sgn * (ew + 4), eyeY - 3, cx + sgn * (hw + 1), eyeY - 5));
                }
                g.draw(new Arc2D.Double(cx - 5, eyeY - 5, 10, 6, 0, 180, Arc2D.OPEN));
            }
            // ---- front hair
            frontHair(g, L, hair, cx, cy, hw, hh, jawW);
            g.setTransform(saved);
            g.setClip(oldClip);
            if (framed) {
                g.setColor(new Color(255, 255, 255, 50));
                g.setStroke(new BasicStroke(1f));
                g.draw(new RoundRectangle2D.Double(x + 0.5, y + 0.5, w - 1, h - 1, 22 * w / 200, 22 * h / 240));
            }
        }

        static void star(Graphics2D g, double x, double y, double r) {
            Path2D p = new Path2D.Double();
            for (int i = 0; i < 10; i++) {
                double a = -Math.PI / 2 + i * Math.PI / 5, rr = i % 2 == 0 ? r : r * 0.45;
                if (i == 0) p.moveTo(x + Math.cos(a) * rr, y + Math.sin(a) * rr); else p.lineTo(x + Math.cos(a) * rr, y + Math.sin(a) * rr);
            }
            p.closePath();
            g.fill(p);
        }

        /** Shoulders, jacket, shirt and tie (or blouse). */
        static void body(Graphics2D g, Cand c, Look L, double breath) {
            double by = breath;
            Color suit = SUIT[clampi(L.suit, 0, SUIT.length - 1)];
            boolean casual = L.suit == 3, tee = L.suit == 4, vest = L.suit == 5;
            Path2D sh = new Path2D.Double();
            sh.moveTo(-4, 244);
            sh.lineTo(2, 214 + by);
            sh.curveTo(10, 192 + by, 40, 184 + by, 72, 178 + by);
            sh.lineTo(128, 178 + by);
            sh.curveTo(160, 184 + by, 190, 192 + by, 198, 214 + by);
            sh.lineTo(204, 244);
            sh.closePath();
            Color jacket = vest ? rgb(0xe9eef6) : suit;
            g.setPaint(new GradientPaint(0, (float) (180 + by), shade(jacket, 1.12), 0, 244, shade(jacket, 0.7)));
            g.fill(sh);
            if (casual) {
                // open-collar shirt
                g.setColor(shade(suit, 0.8));
                g.setStroke(new BasicStroke(1.2f));
                g.draw(new Line2D.Double(100, 196 + by, 100, 244));
                for (int i = 0; i < 3; i++) g.fill(new Ellipse2D.Double(98.5, 206 + i * 13 + by, 3, 3));
                Path2D v = new Path2D.Double();
                v.moveTo(84, 180 + by); v.lineTo(100, 202 + by); v.lineTo(116, 180 + by); v.closePath();
                g.setColor(shade(Portrait.SKIN[clampi(L.skin, 0, 7)], 0.85));
                g.fill(v);
                collar(g, 100, 178 + by, shade(suit, 1.12), true);
                return;
            }
            if (vest) {
                // shirt sleeves + fleece vest
                Path2D vv = new Path2D.Double();
                vv.moveTo(36, 244); vv.lineTo(44, 196 + by); vv.curveTo(60, 186 + by, 74, 182 + by, 86, 182 + by);
                vv.lineTo(100, 210 + by); vv.lineTo(114, 182 + by);
                vv.curveTo(126, 182 + by, 140, 186 + by, 156, 196 + by); vv.lineTo(164, 244); vv.closePath();
                g.setPaint(new GradientPaint(0, (float) (186 + by), rgb(0x2a3a5a), 0, 244, rgb(0x172238)));
                g.fill(vv);
                g.setColor(new Color(220, 220, 230, 160));
                g.draw(new Line2D.Double(100, 210 + by, 100, 244));
                collar(g, 100, 178 + by, rgb(0xdfe6f2), true);
                return;
            }
            // V of shirt or blouse
            Color shirt = L.female ? BLOUSE[clampi(L.tie, 0, BLOUSE.length - 1)] : (tee ? rgb(0x22252b) : rgb(0xf2f4f8));
            Path2D v = new Path2D.Double();
            double vDepth = L.female ? 226 : tee ? 214 : 244;
            v.moveTo(78, 180 + by);
            v.lineTo(100, vDepth + by);
            v.lineTo(122, 180 + by);
            v.closePath();
            g.setColor(shirt);
            g.fill(v);
            if (L.female && clampi(L.tie, 0, 9) == 0) {
                // a necklace
                g.setColor(new Color(245, 240, 230, 220));
                for (int i = 0; i < 9; i++) {
                    double a = Math.PI * (0.15 + 0.7 * i / 8.0);
                    g.fill(new Ellipse2D.Double(100 + Math.cos(a) * 15 - 1.6, 182 + by + Math.sin(a) * 12 - 1.6, 3.2, 3.2));
                }
            }
            if (!L.female && !tee) {
                collar(g, 100, 178 + by, rgb(0xf7f8fb), false);
                Color tc = TIE[clampi(L.tie, 0, TIE.length - 1)];
                if (tc != null) {
                    Path2D knot = new Path2D.Double();
                    knot.moveTo(95, 186 + by); knot.lineTo(105, 186 + by); knot.lineTo(103, 194 + by); knot.lineTo(97, 194 + by); knot.closePath();
                    Path2D blade = new Path2D.Double();
                    blade.moveTo(97, 194 + by); blade.lineTo(103, 194 + by); blade.lineTo(108, 236 + by); blade.lineTo(100, 244 + by); blade.lineTo(92, 236 + by); blade.closePath();
                    g.setPaint(new GradientPaint(92, 0, shade(tc, 1.15), 108, 0, shade(tc, 0.8)));
                    g.fill(blade);
                    g.setColor(shade(tc, 0.85));
                    g.fill(knot);
                    if (L.tie == 2) {
                        g.setColor(new Color(255, 255, 255, 70));
                        g.setStroke(new BasicStroke(1.6f));
                        for (int i = 0; i < 5; i++) g.draw(new Line2D.Double(92, 200 + i * 9 + by, 108, 194 + i * 9 + by));
                    }
                }
            }
            // lapels
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                Path2D lap = new Path2D.Double();
                lap.moveTo(100 + sgn * 22, 179 + by);
                lap.lineTo(100 + sgn * 4, (L.female ? 226 : 236) + by);
                lap.lineTo(100 + sgn * 16, 216 + by);
                lap.lineTo(100 + sgn * 30, 200 + by);
                lap.lineTo(100 + sgn * 26, 182 + by);
                lap.closePath();
                g.setColor(shade(suit, 0.82));
                g.fill(lap);
                g.setColor(shade(suit, 1.35));
                g.setStroke(new BasicStroke(0.9f));
                g.draw(lap);
            }
            // flag pin
            if (c.party <= REP && !tee) {
                double px = 136, py = 206 + by;
                g.setColor(rgb(0xb5243a)); g.fill(new Rectangle2D.Double(px, py, 9, 6));
                g.setColor(Color.white); g.fill(new Rectangle2D.Double(px, py + 1.5, 9, 1)); g.fill(new Rectangle2D.Double(px, py + 3.5, 9, 1));
                g.setColor(rgb(0x1d3a8a)); g.fill(new Rectangle2D.Double(px, py, 4, 3));
            }
        }

        static void collar(Graphics2D g, double cx, double y, Color col, boolean open) {
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                Path2D p = new Path2D.Double();
                p.moveTo(cx + sgn * 17, y - 2);
                p.lineTo(cx + sgn * (open ? 22 : 2), y + (open ? 20 : 12));
                p.lineTo(cx + sgn * (open ? 10 : 15), y + (open ? 24 : 14));
                p.lineTo(cx + sgn * 20, y + 2);
                p.closePath();
                g.setColor(col);
                g.fill(p);
                g.setColor(new Color(0, 0, 0, 50));
                g.draw(p);
            }
        }

        static void backHair(Graphics2D g, Look L, Color hair, double cx, double cy, double hw, double hh) {
            double len = L.style == 6 ? 0.62 : L.style == 7 ? 1.3 : 1.65;
            double wid = L.style == 8 ? 1.32 : L.style == 6 ? 1.14 : 1.2;
            Path2D p = new Path2D.Double();
            p.moveTo(cx - hw * wid, cy + hh * len);
            p.curveTo(cx - hw * (wid + 0.12), cy - hh * 0.2, cx - hw * 1.2, cy - hh * 1.38, cx, cy - hh * 1.2);
            p.curveTo(cx + hw * 1.2, cy - hh * 1.38, cx + hw * (wid + 0.12), cy - hh * 0.2, cx + hw * wid, cy + hh * len);
            if (L.style == 8) {
                for (int i = 6; i >= 0; i--) {
                    double xx = cx - hw * wid + (hw * wid * 2) * i / 6.0;
                    p.lineTo(xx, cy + hh * len + (i % 2 == 0 ? 6 : -2));
                }
            } else p.quadTo(cx, cy + hh * (len + 0.08), cx - hw * wid, cy + hh * len);
            p.closePath();
            g.setPaint(new GradientPaint((float) cx, (float) (cy - hh), shade(hair, 1.05), (float) cx, (float) (cy + hh * len), shade(hair, 0.62)));
            g.fill(p);
        }

        static void frontHair(Graphics2D g, Look L, Color hair, double cx, double cy, double hw, double hh, double jawW) {
            int st = L.style;
            Color hi = shade(hair, L.hair >= 5 ? 1.12 : 1.45), dk = shade(hair, 0.7);
            g.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (st == 0) {
                if (L.hair >= 6 || L.gray > 0.5) {
                    // horseshoe: hair only on the sides
                    for (int sgn = -1; sgn <= 1; sgn += 2) {
                        Path2D p = new Path2D.Double();
                        p.moveTo(cx + sgn * (hw + 1), cy + 4);
                        p.curveTo(cx + sgn * (hw + 3), cy - 18, cx + sgn * (hw - 2), cy - 30, cx + sgn * (hw - 9), cy - 36);
                        p.lineTo(cx + sgn * (hw - 12), cy - 32);
                        p.curveTo(cx + sgn * (hw - 6), cy - 22, cx + sgn * (hw - 3), cy - 10, cx + sgn * (hw - 3), cy + 4);
                        p.closePath();
                        g.setColor(alpha(hair, 0.9));
                        g.fill(p);
                    }
                } else {
                    Path2D cap = new Path2D.Double();
                    cap.moveTo(cx - hw, cy - 6);
                    cap.curveTo(cx - hw, cy - hh * 1.36, cx + hw, cy - hh * 1.36, cx + hw, cy - 6);
                    cap.curveTo(cx + hw * 0.8, cy - hh * 0.6, cx - hw * 0.8, cy - hh * 0.6, cx - hw, cy - 6);
                    g.setColor(alpha(hair, 0.18));
                    g.fill(cap);
                }
                // scalp highlight
                g.setColor(new Color(255, 255, 255, 34));
                g.fill(new Ellipse2D.Double(cx - 18, cy - hh * 0.98, 30, 12));
                return;
            }
            if (st == 6 || st == 7 || st == 8 || st == 9) {
                // women's front: a domed cap with a side-swept fringe, plus side curtains framing the face
                boolean center = st == 8;
                double part = center ? cx : cx - hw * 0.32;
                double low = st == 6 ? hh * 0.42 : st == 7 ? hh * 0.78 : hh * 1.0;
                Area front = new Area();
                Path2D cap = new Path2D.Double();
                cap.moveTo(cx - hw - 3, cy + 2);
                cap.curveTo(cx - hw - 4, cy - hh * 1.42, cx + hw + 4, cy - hh * 1.42, cx + hw + 3, cy + 2);
                cap.lineTo(cx + hw - 3, cy + 2);
                if (center) {
                    cap.curveTo(cx + hw - 1, cy - hh * 0.45, cx + hw * 0.45, cy - hh * 0.7, part, cy - hh * 0.74);
                    cap.curveTo(cx - hw * 0.45, cy - hh * 0.7, cx - hw + 1, cy - hh * 0.45, cx - hw + 3, cy + 2);
                } else {
                    cap.curveTo(cx + hw - 1, cy - hh * 0.4, cx + hw * 0.5, cy - hh * 0.66, part + 3, cy - hh * 0.76);
                    cap.curveTo(part - hw * 0.2, cy - hh * 0.55, cx - hw * 0.62, cy - hh * 0.42, cx - hw + 3, cy - hh * 0.12);
                    cap.lineTo(cx - hw + 3, cy + 2);
                }
                cap.closePath();
                front.add(new Area(cap));
                for (int sgn = -1; sgn <= 1; sgn += 2) {
                    Path2D cur = new Path2D.Double();
                    cur.moveTo(cx + sgn * (hw + 3), cy - hh * 0.25);
                    cur.curveTo(cx + sgn * (hw + 7), cy + low * 0.4, cx + sgn * (hw + 6), cy + low * 0.8, cx + sgn * (hw + 3), cy + low);
                    cur.lineTo(cx + sgn * (hw - 6), cy + low * 0.92);
                    cur.curveTo(cx + sgn * (hw - 2), cy + low * 0.6, cx + sgn * (hw - 1), cy + low * 0.2, cx + sgn * (hw - 3), cy - hh * 0.1);
                    cur.closePath();
                    front.add(new Area(cur));
                }
                g.setPaint(new GradientPaint((float) cx, (float) (cy - hh * 1.1), shade(hair, 1.12), (float) cx, (float) (cy + low), shade(hair, 0.82)));
                g.fill(front);
                Shape pc = g.getClip();
                g.clip(front);
                g.setColor(alpha(hi, 0.30));
                for (int i = 0; i < 9; i++) {
                    Path2D sl = new Path2D.Double();
                    double sx = part + (i - 4) * 8.5;
                    sl.moveTo(part + (i - 4) * 1.5, cy - hh * 1.02);
                    sl.quadTo(sx + (sx < part ? -6 : 6), cy - hh * 0.7, sx + (sx < part ? -14 : 14), cy - hh * 0.1);
                    g.draw(sl);
                }
                for (int sgn = -1; sgn <= 1; sgn += 2) {
                    g.draw(new Line2D.Double(cx + sgn * (hw + 2), cy - hh * 0.1, cx + sgn * (hw + 1), cy + low * 0.9));
                    g.draw(new Line2D.Double(cx + sgn * (hw - 1), cy - hh * 0.05, cx + sgn * (hw - 3), cy + low * 0.85));
                }
                g.setClip(pc);
                return;
            }
            // men's short styles: a cap over the cranium with a hairline
            double top = st == 11 ? 1.48 : st == 5 || st == 10 ? 1.44 : st == 3 ? 1.40 : st == 1 ? 1.33 : 1.38;
            double line = st == 4 ? 0.86 : st == 3 || st == 11 ? 0.70 : st == 1 ? 0.66 : st == 5 || st == 10 ? 0.58 : 0.62;
            Path2D p = new Path2D.Double();
            p.moveTo(cx - hw - 1.5, cy + 2);
            p.curveTo(cx - hw - 3, cy - hh * top, cx + hw + 3, cy - hh * top, cx + hw + 1.5, cy + 2);
            p.lineTo(cx + hw - 3, cy + 2);
            if (st == 4) {
                // receding: temples pushed back, thin centre
                p.curveTo(cx + hw - 2, cy - hh * 0.5, cx + hw * 0.55, cy - hh * 0.95, cx + hw * 0.25, cy - hh * 0.8);
                p.quadTo(cx, cy - hh * 0.72, cx - hw * 0.25, cy - hh * 0.8);
                p.curveTo(cx - hw * 0.55, cy - hh * 0.95, cx - hw + 2, cy - hh * 0.5, cx - hw + 3, cy + 2);
            } else {
                double partX = st == 2 ? cx - hw * 0.38 : cx;
                p.curveTo(cx + hw - 2, cy - hh * 0.4, cx + hw * 0.7, cy - hh * line, partX + hw * 0.2, cy - hh * line);
                if (st == 2 || st == 5 || st == 10) p.quadTo(partX, cy - hh * (line - 0.06), partX - hw * 0.3, cy - hh * (line - 0.02));
                p.curveTo(cx - hw * 0.75, cy - hh * line, cx - hw + 2, cy - hh * 0.4, cx - hw + 3, cy + 2);
            }
            p.closePath();
            g.setPaint(new GradientPaint((float) cx, (float) (cy - hh * top), shade(hair, 1.1), (float) cx, (float) (cy - hh * 0.3), shade(hair, 0.85)));
            g.fill(p);
            double capTop = cy - hh * top * 0.75;
            // volume on top: soft waves (5, 10) or a swept-back pompadour (11)
            if (st == 5 || st == 10) {
                int n = st == 10 ? 6 : 5;
                for (int i = 0; i < n; i++) {
                    double a = i / (double) (n - 1);
                    double ex = cx - hw * 0.78 + a * hw * 1.56, ey = capTop + 6 + Math.abs(a - 0.5) * 14 + (st == 10 && i % 2 == 1 ? -3 : 0);
                    double r = st == 10 ? 13 : 12;
                    g.setColor(i % 2 == 0 ? shade(hair, 1.04) : shade(hair, 0.95));
                    g.fill(new Ellipse2D.Double(ex - r, ey - r * 0.85, r * 2, r * 1.6));
                }
            } else if (st == 11) {
                Path2D v = new Path2D.Double();
                v.moveTo(cx - hw * 0.95, cy - hh * 0.55);
                v.curveTo(cx - hw * 1.05, capTop - 10, cx + hw * 0.2, capTop - 20, cx + hw * 0.85, capTop + 4);
                v.curveTo(cx + hw * 1.0, cy - hh * 0.75, cx + hw * 0.6, cy - hh * 0.75, cx - hw * 0.2, cy - hh * 0.7);
                v.closePath();
                g.setPaint(new GradientPaint((float) cx, (float) (capTop - 18), shade(hair, 1.18), (float) cx, (float) (cy - hh * 0.6), shade(hair, 0.9)));
                g.fill(v);
            }
            // texture strokes
            g.setColor(alpha(hi, 0.30));
            g.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 0; i < 8; i++) {
                double a = i / 7.0;
                double sx = cx - hw * 0.7 + a * hw * 1.4;
                double ty = capTop + 4 + Math.abs(a - 0.5) * 10;
                Path2D s = new Path2D.Double();
                if (st == 3 || st == 11) {
                    s.moveTo(sx, cy - hh * (line + 0.04));
                    s.quadTo(sx * 0.9 + cx * 0.1, (ty + cy - hh * line) / 2, sx * 0.75 + cx * 0.25, ty);
                } else {
                    s.moveTo(sx, ty);
                    s.quadTo(sx + 5, (ty + cy - hh * line) / 2, sx + 2 + (a - 0.5) * 8, cy - hh * (line + 0.06));
                }
                g.draw(s);
            }
            if (st == 2) {
                g.setColor(alpha(dk, 0.8));
                g.setStroke(new BasicStroke(1.4f));
                g.draw(new Line2D.Double(cx - hw * 0.38, cy - hh * (line + 0.02), cx - hw * 0.33, cy - hh * (line + 0.22)));
            }
            // sideburns, inside the face outline
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                Path2D sb = new Path2D.Double();
                sb.moveTo(cx + sgn * (hw + 0.5), cy - 10);
                sb.lineTo(cx + sgn * (hw + 0.2), cy + 10);
                sb.lineTo(cx + sgn * (hw - 4.5), cy + 9);
                sb.lineTo(cx + sgn * (hw - 5), cy - 10);
                sb.closePath();
                g.setColor(alpha(hair, 0.92));
                g.fill(sb);
            }
        }
    }

    // =====================================================================
    //  22.  THE MAP OF THE UNITED STATES
    // =====================================================================
    static final int MM_BLANK = 0, MM_MONEY = 1, MM_INTERNAL = 2, MM_PUBLIC = 3, MM_FIELD = 4, MM_CAL = 5, MM_BALLOT = 6;
    static final String[] MAP_MODE = {"Blank", "Spending", "Internal polls", "Public polls", "Volunteers & doors", "Primary calendar", "Ballot access"};
    static final String[] MAP_MODE_TIP = {
            "A plain map: click a state to open it.",
            "Where your money goes: your campaign's spending in each state over the last few weeks. Deeper green = deeper investment.",
            "Your own polling. Blue and red by who leads; the better and fresher your poll, the more solid the colour. Unpolled states are hatched.",
            "The public polling average in each state, as the press reports it. Few polls = faint colours.",
            "Your volunteer army: shifts worked this week (colour) and doors knocked in total (label).",
            "The primary calendar: results so far and the dates still to come.",
            "Where you're on the ballot, where petitions are under way, and where you missed the deadline."};
    /** Small eastern states get labelled boxes off the coast. */
    static final String[] CALLOUTS = {"VT", "NH", "MA", "RI", "CT", "NJ", "DE", "MD", "DC"};

    static final class USMap {
        static Path2D[] shape = new Path2D[NS];
        static Rectangle2D[] bounds = new Rectangle2D[NS];
        static volatile boolean ready;
        static Color[] cur = new Color[NS];
        static double[] hoverAmt = new double[NS];
        static int hoverState = -1;
        static final double AW = 975, AH = 610;

        static int dec(char c) { int v = c; if (v > 92) v--; return v - 35; }

        static int num(String s, int i, int n) { int v = 0; for (int k = 0; k < n; k++) v = v * 90 + dec(s.charAt(i + k)); return v; }

        static synchronized void load() {
            if (ready) return;
            for (String[] row : SHAPES) {
                int s = stateIdx(row[0]);
                if (s < 0) continue;
                Path2D.Double p = new Path2D.Double(Path2D.WIND_EVEN_ODD);
                for (String ring : row[1].split("~")) {
                    if (ring.length() < 6) continue;
                    int x = num(ring, 0, 3), y = num(ring, 3, 3);
                    p.moveTo((x - 400) / 4.0, (y - 200) / 4.0);
                    int i = 6;
                    while (i < ring.length()) {
                        if (ring.charAt(i) == '!') { x = num(ring, i + 1, 3); y = num(ring, i + 4, 3); i += 7; }
                        else { x += dec(ring.charAt(i)) - 44; y += dec(ring.charAt(i + 1)) - 44; i += 2; }
                        p.lineTo((x - 400) / 4.0, (y - 200) / 4.0);
                    }
                    p.closePath();
                }
                shape[s] = p;
                bounds[s] = p.getBounds2D();
            }
            for (int s = 0; s < NS; s++) cur[s] = C_STATE;
            ready = true;
        }

        static void animate(double dt) {
            for (int s = 0; s < NS; s++) hoverAmt[s] += ((s == hoverState ? 1 : 0) - hoverAmt[s]) * Math.min(1, dt * 12);
        }

        /** Map geometry for a target rectangle: {scale, offsetX, offsetY}. Leaves room on the right for the callout boxes. */
        static double[] fit(double x, double y, double w, double h) {
            double s = Math.min((w - 84) / AW, h / AH);
            return new double[]{s, x + (w - 84 - AW * s) / 2, y + (h - AH * s) / 2};
        }

        static int stateAt(double[] f, double px, double py) {
            if (!ready) return -1;
            double ax = (px - f[1]) / f[0], ay = (py - f[2]) / f[0];
            for (int s = 0; s < NS; s++) if (shape[s] != null && bounds[s].contains(ax, ay) && shape[s].contains(ax, ay)) return s;
            for (int i = 0; i < CALLOUTS.length; i++) {
                double[] b = calloutBox(f, i);
                if (px >= b[0] && py >= b[1] && px < b[0] + b[2] && py < b[1] + b[3]) return stateIdx(CALLOUTS[i]);
            }
            return -1;
        }

        static double[] calloutBox(double[] f, int i) {
            double bx = f[1] + AW * f[0] + 8, by = f[2] + 95 * f[0] + i * 22;
            return new double[]{bx, by, 76, 19};
        }

        /** Per-state fill colour and label for a map mode, from the player's point of view. */
        static Color colour(Battleground2028 u, Game g, int mode, int s, String[] lab) {
            Camp me = g.me();
            lab[0] = null;
            switch (mode) {
                case MM_MONEY: {
                    if (me == null) return C_STATE;
                    double max = 1;
                    for (int t = 0; t < NS; t++) max = Math.max(max, me.spend4[t]);
                    double v = me.spend4[s];
                    if (v < 1000) return C_STATE;
                    double f = Math.pow(v / max, 0.55);
                    lab[0] = money(v / 4);
                    return mix(rgb(0xc8efd8), rgb(0x0a5c33), f);
                }
                case MM_INTERNAL: {
                    if (me == null) return C_STATE;
                    if (Hq.primaryView(g)) {
                        InternalPoll p = Polls.latest(g, me.idx, s);
                        if (p == null || p.share == null) return hatch();
                        int lead = leader(p.share, g, me.party);
                        double conf = pollConfidence(g, p);
                        lab[0] = Math.round(p.share[me.idx] * 100) + "%";
                        return mix(C_STATE, candColour(g, lead), 0.35 + 0.65 * conf);
                    }
                    InternalPoll p = Polls.latest(g, me.idx, s);
                    if (p == null) return hatch();
                    double conf = pollConfidence(g, p);
                    if (!Double.isNaN(p.ind)) return threeWay(p.margin, p.ind, 0.25 + 0.75 * conf, lab);
                    lab[0] = margin(p.margin);
                    return marginColor(p.margin, 0.25 + 0.75 * conf);
                }
                case MM_PUBLIC: {
                    if (Hq.primaryView(g)) {
                        double[] sh = Hq.statePrimaryPoll(g, me != null ? me.party : DEM, s);
                        if (sh == null) return C_STATE;
                        int lead = leader(sh, g, me != null ? me.party : DEM);
                        if (lead < 0) return C_STATE;
                        lab[0] = g.camp(lead).last().length() > 8 ? g.camp(lead).last().substring(0, 7) + "." : g.camp(lead).last();
                        return mix(C_STATE, candColour(g, lead), 0.8);
                    }
                    double conf = g.pubN[s] >= 3 ? 1 : g.pubN[s] >= 1 ? 0.7 : 0.35;
                    if (Polls.indRun(g)) return threeWay(g.pubAvg[s], g.pubI[s], conf, lab);
                    lab[0] = margin(g.pubAvg[s]);
                    return marginColor(g.pubAvg[s], conf);
                }
                case MM_FIELD: {
                    if (me == null) return C_STATE;
                    double max = 1;
                    for (int t = 0; t < NS; t++) max = Math.max(max, me.shiftsW[t]);
                    double v = me.shiftsW[s];
                    if (me.doorsT[s] >= 500) lab[0] = compact(me.doorsT[s]);
                    if (v < 1) return C_STATE;
                    return mix(rgb(0x3b3f8f), rgb(0xb98cff), Math.pow(v / max, 0.6));
                }
                case MM_CAL: {
                    int party = me != null ? me.party : DEM;
                    Contest c = Hq.contest(g, party, s);
                    if (c == null) return C_STATE;
                    if (c.done && c.result != null) {
                        int win = leader(c.result, g, party);
                        lab[0] = win >= 0 ? g.camp(win).last() : "";
                        if (lab[0].length() > 8) lab[0] = lab[0].substring(0, 7) + ".";
                        return win >= 0 ? candColour(g, win) : C_STATE;
                    }
                    int[] d = dateOf(c.week * 7 + 1);
                    lab[0] = MONTHS[d[1]] + " " + d[2];
                    int away = c.week - g.week;
                    return mix(rgb(0x6b5a2a), rgb(0x2a3650), clamp(away / 20.0, 0, 1));
                }
                case MM_BALLOT: {
                    Camp k = g.indep();
                    if (k == null || k.onBallot == null) return C_STATE;
                    Ballot.init();
                    if (k.onBallot[s]) { lab[0] = "on"; return mix(C_STATE, C_IND, 0.85); }
                    if (g.week > Ballot.deadline[s]) { lab[0] = "write-in"; return rgb(0x5a2a2e); }
                    double pct = Math.min(1, Ballot.valid(k, s) / Math.max(1, Ballot.need[s]));
                    lab[0] = Math.round(pct * 100) + "%";
                    return mix(rgb(0x2a3650), rgb(0x8a6a1a), pct);
                }
                default:
                    return C_STATE;
            }
        }

        /** Three-way race: the leader's colour, shaded by the lead over second place. */
        static Color threeWay(double m, double ind, double conf, String[] lab) {
            double d = (100 - ind) / 2 + m / 2, r = (100 - ind) / 2 - m / 2;
            if (ind > d && ind > r) {
                double lead = ind - Math.max(d, r);
                lab[0] = "I+" + Math.round(lead);
                return mix(C_STATE, mix(rgb(0xfcd58a), rgb(0xa8620a), clamp(lead / 20, 0, 1)), clamp(conf, 0.15, 1));
            }
            double lead = d >= r ? d - Math.max(r, ind) : -(r - Math.max(d, ind));
            lab[0] = margin(lead);
            return marginColor(lead, conf);
        }

        static Color hatch() { return rgb(0x252f44); }

        static int leader(double[] sh, Game g, int party) {
            int best = -1;
            for (int i = 0; i < sh.length && i < g.camps.size(); i++) if (g.camps.get(i).party == party && (best < 0 || sh[i] > sh[best])) best = i;
            return best;
        }

        static double pollConfidence(Game g, InternalPoll p) {
            double q = POLL_QUALITY[p.method] * Math.sqrt(p.n / 1000.0);
            double age = g.week - p.week;
            return clamp(q * Math.exp(-age / 7.0), 0.1, 1);
        }

        static final Color[] CAND_COL = {rgb(0x4f9dff), rgb(0xff7a59), rgb(0x3fd0a8), rgb(0xf2c14e), rgb(0xc07bff), rgb(0xff5f9e),
                rgb(0x7fdc4f), rgb(0x59d4ff), rgb(0xffa94d), rgb(0x9aa8ff), rgb(0xe36f6f), rgb(0x6fe3c1)};

        /** A stable colour for each primary candidate (by order of opening poll within the party). */
        static Color candColour(Game g, int idx) {
            Camp k = g.camp(idx);
            if (k == null) return C_STATE;
            int rank = 0;
            for (Camp o : g.camps) if (o.party == k.party && o.c.poll > k.c.poll) rank++;
            return CAND_COL[rank % CAND_COL.length];
        }

        /** Draws the map in a rectangle. Returns the fit used (for hit testing). */
        static double[] draw(Battleground2028 u, Graphics2D g, Game game, double x, double y, double w, double h, int mode, int selected, boolean interactive) {
            double[] f = fit(x, y, w, h);
            if (!ready) { textC(g, "Loading map...", x + w / 2, y + h / 2, F_B, C_DIM); return f; }
            int hov = interactive && u.modal == null && u.popup == null ? stateAt(f, u.mx, u.my) : -1;
            if (interactive) hoverState = hov;
            String[] lab = new String[1];
            String[] labels = new String[NS];
            Color[] target = new Color[NS];
            for (int s = 0; s < NS; s++) {
                target[s] = game == null ? C_STATE : colour(u, game, mode, s, lab);
                labels[s] = lab[0];
                cur[s] = mix(cur[s] == null ? target[s] : cur[s], target[s], 0.18);
            }
            AffineTransform at = g.getTransform();
            // drop shadow
            g.translate(f[1], f[2]);
            g.scale(f[0], f[0]);
            g.translate(3, 5);
            g.setColor(new Color(0, 0, 0, 90));
            for (int s = 0; s < NS; s++) if (shape[s] != null) g.fill(shape[s]);
            g.translate(-3, -5);
            float edge = (float) (1.0 / f[0]);
            for (int s = 0; s < NS; s++) {
                if (shape[s] == null) continue;
                Color c = cur[s];
                if (hoverAmt[s] > 0.01) c = mix(c, Color.white, 0.18 * hoverAmt[s]);
                g.setColor(c);
                g.fill(shape[s]);
                if (mode == MM_INTERNAL && target[s].equals(hatch())) {
                    Shape pc = g.getClip();
                    g.clip(shape[s]);
                    g.setColor(new Color(255, 255, 255, 18));
                    g.setStroke(new BasicStroke(1.2f * edge));
                    Rectangle2D b = bounds[s];
                    for (double d = -b.getHeight(); d < b.getWidth(); d += 7) g.draw(new Line2D.Double(b.getX() + d, b.getY() + b.getHeight(), b.getX() + d + b.getHeight(), b.getY()));
                    g.setClip(pc);
                }
            }
            g.setColor(new Color(6, 10, 20, 230));
            g.setStroke(new BasicStroke(0.9f * edge, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int s = 0; s < NS; s++) if (shape[s] != null) g.draw(shape[s]);
            if (hov >= 0 && shape[hov] != null) {
                g.setColor(new Color(255, 255, 255, 200));
                g.setStroke(new BasicStroke(1.6f * edge, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(shape[hov]);
            }
            if (selected >= 0 && shape[selected] != null) {
                float pulse = (float) (0.75 + 0.25 * Math.sin(u.tsec * 4));
                g.setColor(new Color(242, 193, 78, (int) (255 * pulse)));
                g.setStroke(new BasicStroke(2.6f * edge, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(shape[selected]);
            }
            // candidate location marker
            g.setTransform(at);
            Camp me = game != null ? game.me() : null;
            // labels
            for (int s = 0; s < NS; s++) {
                if (shape[s] == null || isCallout(s)) continue;
                double lx = f[1] + STATES[s].cx * f[0], ly = f[2] + STATES[s].cy * f[0];
                if (STATES[s].ab.equals("FL")) lx += 14 * f[0];
                if (STATES[s].ab.equals("MI")) { lx += 8 * f[0]; ly += 14 * f[0]; }
                if (STATES[s].ab.equals("LA")) lx -= 10 * f[0];
                if (STATES[s].ab.equals("HI")) { lx += 10 * f[0]; ly -= 4 * f[0]; }
                if (STATES[s].ab.equals("KY")) ly += 4 * f[0];
                Color bg = cur[s];
                Color tc = luminance(bg) > 0.55 ? rgb(0x0b1220) : Color.white;
                boolean big = bounds[s].getWidth() * f[0] > 40 && bounds[s].getHeight() * f[0] > 26;
                textC(g, STATES[s].ab, lx, ly + (labels[s] != null && big ? -1 : 4), F_TB, alpha(tc, 0.92));
                if (labels[s] != null && big) textC(g, labels[s], lx, ly + 10, F_T, alpha(tc, 0.85));
            }
            // callouts for the small north-eastern states
            for (int i = 0; i < CALLOUTS.length; i++) {
                int s = stateIdx(CALLOUTS[i]);
                double[] b = calloutBox(f, i);
                Color c = cur[s];
                if (hov == s) c = mix(c, Color.white, 0.18);
                RoundRectangle2D rr = new RoundRectangle2D.Double(b[0], b[1], b[2], b[3], 6, 6);
                g.setColor(c);
                g.fill(rr);
                g.setColor(s == selected ? C_GOLD : new Color(255, 255, 255, 40));
                g.setStroke(new BasicStroke(s == selected ? 2f : 1f));
                g.draw(rr);
                Color tc = luminance(c) > 0.55 ? rgb(0x0b1220) : Color.white;
                text(g, CALLOUTS[i], b[0] + 5, b[1] + 13.5, F_TB, tc);
                if (labels[s] != null) textR(g, labels[s].length() > 7 ? labels[s].substring(0, 7) : labels[s], b[0] + b[2] - 4, b[1] + 13.5, F_T, alpha(tc, 0.85));
                // leader line to the state
                double sx = f[1] + STATES[s].cx * f[0], sy = f[2] + STATES[s].cy * f[0];
                g.setColor(new Color(255, 255, 255, 40));
                g.setStroke(new BasicStroke(0.8f));
                g.draw(new Line2D.Double(b[0], b[1] + b[3] / 2, sx, sy));
                g.fill(new Ellipse2D.Double(sx - 1.5, sy - 1.5, 3, 3));
            }
            g.setStroke(new BasicStroke(1f));
            // the candidate's whereabouts
            if (me != null && me.location >= 0 && me.location < NS && interactive) {
                double lx = f[1] + STATES[me.location].cx * f[0], ly = f[2] + STATES[me.location].cy * f[0] - 18;
                double bob = Math.sin(u.tsec * 3) * 2;
                g.setColor(new Color(0, 0, 0, 120));
                g.fill(new Ellipse2D.Double(lx - 6, ly + 17, 12, 4));
                Portrait.avatar(g, me.c, lx, ly + bob, 10, C_GOLD);
            }
            return f;
        }

        static boolean isCallout(int s) { for (String c : CALLOUTS) if (STATES[s].ab.equals(c)) return true; return false; }

        static double luminance(Color c) { return (0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue()) / 255.0; }

        /** Legend under the map for the current mode. */
        static void legend(Graphics2D g, Game game, int mode, double x, double y, double w) {
            switch (mode) {
                case MM_MONEY:
                    gradientBar(g, x, y, 160, rgb(0xc8efd8), rgb(0x0a5c33));
                    text(g, "less", x, y + 22, F_T, C_DIM); textR(g, "more", x + 160, y + 22, F_T, C_DIM);
                    text(g, "your spending per state, last four weeks", x + 176, y + 10, F_T, C_FAINT);
                    break;
                case MM_INTERNAL: case MM_PUBLIC:
                    if (game != null && Hq.primaryView(game)) { text(g, "Colour = candidate leading the state" + (mode == MM_INTERNAL ? " in your polling" : ""), x, y + 10, F_T, C_DIM); break; }
                    for (int i = 0; i < 7; i++) {
                        double m = (i - 3) * 6;
                        g.setColor(marginColor(m == 0 ? 0.2 : m, 1));
                        g.fill(new Rectangle2D.Double(x + i * 26, y, 26, 10));
                    }
                    text(g, "R+18", x, y + 22, F_T, C_REP_L); textC(g, "tied", x + 91, y + 22, F_T, C_DIM); textR(g, "D+18", x + 182, y + 22, F_T, C_DEM_L);
                    if (mode == MM_INTERNAL) text(g, "faint = old or small poll   hatched = not polled", x + 200, y + 10, F_T, C_FAINT);
                    else text(g, "faint = few public polls", x + 200, y + 10, F_T, C_FAINT);
                    if (game != null && Polls.indRun(game)) { g.setColor(C_IND); g.fill(new Rectangle2D.Double(x + 200, y + 14, 10, 8)); text(g, "independent leads (labels: lead over 2nd)", x + 214, y + 22, F_T, C_DIM); }
                    break;
                case MM_BALLOT:
                    g.setColor(mix(C_STATE, C_IND, 0.85)); g.fill(new Rectangle2D.Double(x, y, 20, 10)); text(g, "on the ballot", x + 24, y + 10, F_T, C_DIM);
                    g.setColor(rgb(0x8a6a1a)); g.fill(new Rectangle2D.Double(x + 120, y, 20, 10)); text(g, "petitioning (% of signatures)", x + 144, y + 10, F_T, C_DIM);
                    g.setColor(rgb(0x5a2a2e)); g.fill(new Rectangle2D.Double(x + 330, y, 20, 10)); text(g, "missed the deadline", x + 354, y + 10, F_T, C_DIM);
                    break;
                case MM_FIELD:
                    gradientBar(g, x, y, 160, rgb(0x3b3f8f), rgb(0xb98cff));
                    text(g, "volunteer shifts this week", x, y + 22, F_T, C_DIM);
                    text(g, "labels = doors knocked to date", x + 180, y + 10, F_T, C_FAINT);
                    break;
                case MM_CAL:
                    text(g, "Coloured = won (by candidate)   Gold = voting soon   Labels = date or winner", x, y + 10, F_T, C_DIM);
                    break;
                default:
                    text(g, "Click a state to see its voters, polls and your operation there. Right-click to send the candidate.", x, y + 10, F_T, C_DIM);
            }
        }

        static void gradientBar(Graphics2D g, double x, double y, double w, Color a, Color b) {
            g.setPaint(new GradientPaint((float) x, 0, a, (float) (x + w), 0, b));
            g.fill(new RoundRectangle2D.Double(x, y, w, 10, 5, 5));
        }
    }

    // =====================================================================
    //  23.  MENUS: home, new campaign, candidate selection, briefing
    // =====================================================================
    static final class Screens {
        static int briefReturn = SC_HOME;
        static BufferedImage homeBuf;
        static int pickParty = DEM, pickSel = -1, briefTopic = 0, oppSel = -1;
        static List<Cand> partyList(int p) { List<Cand> l = new ArrayList<Cand>(); for (Cand c : ROSTER) if (c.party == p) l.add(c); return l; }

        static void back(Battleground2028 u) {
            switch (u.screen) {
                case SC_SETUP: u.go(SC_HOME); break;
                case SC_PICK: u.go(SC_SETUP); break;
                case SC_CREATE: u.go(SC_PICK); break;
                case SC_PLATFORM: u.go(PlatformEd.returnTo); break;
                case SC_FIELD: u.go(FieldScreen.cand != null && FieldScreen.cand.custom ? SC_CREATE : SC_PICK); break;
                default: u.go(SC_HOME);
            }
        }

        static void platform(Battleground2028 u, Graphics2D g) { PlatformEd.draw(u, g); }

        static void openBriefing(Battleground2028 u) { if (u.screen != SC_BRIEF) { briefReturn = u.screen; u.go(SC_BRIEF); } }

        // ---------------------------------------------------------------- HOME
        static void home(Battleground2028 u, Graphics2D g) {
            u.backdrop(g);
            double t = u.tsec;
            // the shimmering map, fading out under the title
            if (USMap.ready) {
                if (homeBuf == null) homeBuf = new BufferedImage(DW, DH, BufferedImage.TYPE_INT_ARGB);
                Graphics2D b = homeBuf.createGraphics();
                b.setComposite(AlphaComposite.Clear);
                b.fillRect(0, 0, DW, DH);
                b.setComposite(AlphaComposite.SrcOver);
                Portrait.hints(b);
                double[] f = USMap.fit(560, 70, 1020, 640);
                b.translate(f[1], f[2]);
                b.scale(f[0], f[0]);
                for (int s = 0; s < NS; s++) {
                    if (USMap.shape[s] == null) continue;
                    double ph = s * 1.7 + STATES[s].cx * 0.01;
                    double v = Math.sin(t * 0.35 + ph) * 0.5 + 0.5;
                    double lean = clamp((Elect.ready ? Elect.lean[s] : 0) / 25.0, -1, 1);
                    double m = lean * 12 + (v - 0.5) * 14;
                    b.setColor(alpha(marginColor(m, 0.55), 0.6));
                    b.fill(USMap.shape[s]);
                }
                b.setColor(new Color(4, 8, 16, 220));
                b.setStroke(new BasicStroke((float) (0.8 / f[0])));
                for (int s = 0; s < NS; s++) if (USMap.shape[s] != null) b.draw(USMap.shape[s]);
                b.setTransform(new AffineTransform());
                b.setComposite(AlphaComposite.DstIn);
                b.setPaint(new GradientPaint(600, 0, new Color(0, 0, 0, 0), 820, 0, new Color(0, 0, 0, 255)));
                b.fillRect(0, 0, DW, DH);
                b.dispose();
                g.drawImage(homeBuf, 0, 0, null);
            }
            // title block
            double in = easeOut(Math.min(1, u.screenT * 1.4));
            double tx = 80 - (1 - in) * 30;
            label(g, "THE RACE FOR THE WHITE HOUSE", tx + 4, 150, C_GOLD);
            g.setFont(F_HERO);
            g.setColor(Color.white);
            g.drawString("BATTLEGROUND", (float) tx, 232f);
            Font yr = new Font(SERIF, Font.BOLD, 120);
            g.setFont(yr);
            FontMetrics fm = g.getFontMetrics();
            g.setPaint(new GradientPaint((float) tx, 0, rgb(0x4f8dff), (float) (tx + fm.stringWidth("2028")), 0, rgb(0xff5a60)));
            g.drawString("2028", (float) tx, 348f);
            text(g, "Run for president - from the morning after the 2026 midterms to Election Night, November 7, 2028.", tx + 4, 384, F_SERIF_S, C_DIM);
            // menu
            double bx = tx + 4, by = 430, bw = 300, bh = 46;
            u.button(g, bx, by, bw, bh, "New Campaign", B_GOLD, () -> { u.setup = new Settings(); u.go(SC_SETUP); });
            u.button(g, bx, by + 58, bw, bh, "Tutorial: Learn to Play", B_NORM, () -> { u.setup = new Settings(); u.setup.tutorial = true; u.go(SC_SETUP); },
                    "A new campaign with the guided tutorial switched on: tips on every screen, a tour of headquarters, and help the first time you reach debates, primaries and Election Night.");
            boolean has = SaveGame.exists(SaveGame.AUTO);
            u.button(g, bx, by + 116, bw, bh, has ? "Continue  -  " + SaveGame.describe(SaveGame.AUTO) : "Continue", has ? B_PRIMARY : B_OFF, has ? () -> SaveGame.load(u, SaveGame.AUTO) : null);
            u.button(g, bx, by + 174, bw, bh, "Load Game", SaveGame.exists(SaveGame.SLOT) ? B_NORM : B_OFF, SaveGame.exists(SaveGame.SLOT) ? () -> SaveGame.load(u, SaveGame.SLOT) : null,
                    SaveGame.exists(SaveGame.SLOT) ? "Saved: " + SaveGame.describe(SaveGame.SLOT) : "No saved game yet");
            u.button(g, bx, by + 232, bw, bh, "Briefing, Rules & Sources", B_NORM, () -> openBriefing(u));
            u.button(g, bx, by + 290, 144, 38, "Sound: " + (sfx != null && sfx.enabled ? "On" : "Off"), B_GHOST, () -> { if (sfx != null) sfx.enabled = !sfx.enabled; });
            u.button(g, bx + 156, by + 290, 144, 38, "Quit", B_GHOST, () -> { if (u.frame != null) u.frame.dispose(); });
            // featured match-up
            List<Cand> d = partyList(DEM), r = partyList(REP);
            int slot = (int) (t / 7.0);
            double ph = (t / 7.0) - slot;
            Cand cd = d.get((slot * 3) % d.size()), cr = r.get((slot * 5) % r.size());
            double fade = ph < 0.08 ? ph / 0.08 : ph > 0.92 ? (1 - ph) / 0.08 : 1;
            Composite oc = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) clamp(fade, 0, 1)));
            double px = 980, py = 470;
            Portrait.draw(g, cd, px, py, 200, 240, t, 0.25 + 0.25 * Math.sin(t * 0.9), false, true);
            Portrait.draw(g, cr, px + 230, py, 200, 240, t + 2, 0.25 + 0.25 * Math.sin(t * 0.9 + 2), false, true);
            textC(g, cd.name(), px + 100, py + 262, F_BB, C_TEXT);
            textC(g, cr.name(), px + 330, py + 262, F_BB, C_TEXT);
            textC(g, "VS", px + 215, py + 130, new Font(SERIF, Font.BOLD | Font.ITALIC, 30), C_GOLD);
            g.setComposite(oc);
            // marquee of the field
            double my = 808;
            g.setColor(new Color(0, 0, 0, 90));
            g.fill(new Rectangle2D.Double(0, my - 8, DW, 92));
            double off = (t * 22) % (ROSTER.size() * 66);
            for (int i = -1; i < ROSTER.size() * 2; i++) {
                Cand c = ROSTER.get(((i % ROSTER.size()) + ROSTER.size()) % ROSTER.size());
                double x = i * 66 - off;
                if (x < -70 || x > DW) continue;
                Portrait.draw(g, c, x, my, 56, 67, 0, 0, true, true);
            }
            g.setPaint(new GradientPaint(0, 0, new Color(4, 8, 15, 255), 120, 0, new Color(4, 8, 15, 0)));
            g.fill(new Rectangle2D.Double(0, my - 8, 120, 92));
            g.setPaint(new GradientPaint(DW - 120, 0, new Color(4, 8, 15, 0), DW, 0, new Color(4, 8, 15, 255)));
            g.fill(new Rectangle2D.Double(DW - 120, my - 8, 120, 92));
            long days = daysToElection();
            text(g, days > 0 ? thousands(days) + " days until Election Day 2028" : "Election Day 2028", 84, 785, F_XSB, C_GOLD);
            textR(g, "Real people, fictional campaigns: ratings are the game's estimates and every event is invented.", DW - 40, 785, F_XS, C_FAINT);
        }

        static long daysToElection() {
            java.util.Calendar e = new java.util.GregorianCalendar(2028, 10, 7);
            return (e.getTimeInMillis() - System.currentTimeMillis()) / 86400000L;
        }

        // ---------------------------------------------------------------- NEW CAMPAIGN
        static void header(Battleground2028 u, Graphics2D g, String title, String sub) {
            u.backdrop(g);
            text(g, title, 48, 62, F_DISPLAY, Color.white);
            if (sub != null) text(g, sub, 50, 88, F_B, C_DIM);
            g.setColor(new Color(255, 255, 255, 20));
            g.draw(new Line2D.Double(48, 104, DW - 48, 104));
        }

        static void setup(Battleground2028 u, Graphics2D g) {
            Settings s = u.setup;
            header(u, g, "New Campaign", "Set the stage. Everything here can shape your road to 270.");
            // column A
            double ax = 48, ay = 124, aw = 470;
            u.panel(g, ax, ay, aw, 690, "How you play");
            double y = ay + 44;
            text(g, "Mode", ax + 18, y, F_H3, C_TEXT);
            u.segmented(g, ax + 18, y + 10, aw - 36, 36, new String[]{"Full campaign", "General election only"}, s.mode, i -> s.mode = i, null);
            y += 56;
            wrap(g, s.mode == MODE_FULL ? "Start on November 9, 2026, the week after the midterms. Build a campaign through the invisible primary, survive the debates and primaries, win the nomination and then the general. (2-4 hours)"
                    : "Start on Labor Day 2028 as your party's nominee against a chosen opponent. Pick a running mate, negotiate debates and fight for 270. (45-75 minutes)", ax + 18, y + 14, aw - 36, F_XS, C_DIM);
            y += 62;
            text(g, "Difficulty", ax + 18, y, F_H3, C_TEXT);
            u.segmented(g, ax + 18, y + 10, aw - 36, 32, new String[]{"Easy", "Normal", "Hard"}, s.difficulty, i -> s.difficulty = i, null);
            y += 50;
            String[] dd = {"Opponents are slower to adapt; your money stretches further; fewer nasty surprises.", "The race as it is: smart opponents, realistic costs.", "Opponents fight harder, donors are stingier, and scandals bite."};
            wrap(g, dd[s.difficulty], ax + 18, y + 12, aw - 36, F_XS, C_DIM);
            y += 40;
            text(g, "Events", ax + 18, y, F_H3, C_TEXT);
            u.slider(g, ax + 18, y + 22, aw - 36, (s.eventRate - 0.5) / 0.5, C_ACCENT, v -> s.eventRate = 0.5 + 0.5 * v, "How often the world intervenes: crises, scandals, gaffes and viral moments.");
            text(g, "Rare", ax + 18, y + 44, F_T, C_DIM);
            textR(g, "Realistic", ax + aw - 18, y + 44, F_T, C_DIM);
            y += 66;
            text(g, "Third parties in the general election", ax + 18, y, F_H3, C_TEXT);
            String[] tp = {"Green Party - Jill Stein", "Libertarian Party - Dana Whitfield (fictional)", "Forward Party - Andrew Yang", "America Party - Reid Callahan (fictional)"};
            for (int i = 0; i < 4; i++) {
                final int k = i;
                double ry = y + 14 + i * 30;
                u.toggle(g, ax + 18, ry, s.third[i], () -> s.third[k] = !s.third[k], null);
                text(g, tp[i], ax + 64, ry + 15, F_S, s.third[i] ? C_TEXT : C_DIM);
            }
            y += 140;
            text(g, "Start with staff running", ax + 18, y, F_H3, C_TEXT);
            text(g, "You can hand any of these to your staff at any time.", ax + 18, y + 16, F_T, C_DIM);
            for (int i = 0; i < Staff.AREA.length; i++) {
                final int k = i;
                double rx = ax + 18 + (i % 3) * 145, ry = y + 28 + (i / 3) * 28;
                u.toggle(g, rx, ry, s.staff[i], () -> s.staff[k] = !s.staff[k], null);
                text(g, Staff.AREA[i], rx + 44, ry + 15, F_S, s.staff[i] ? C_TEXT : C_DIM);
            }
            y += 96;
            text(g, "Guided tutorial", ax + 18, y, F_H3, C_TEXT);
            u.toggle(g, ax + aw - 18 - 36, y - 14, s.tutorial, () -> { s.tutorial = !s.tutorial; if (s.tutorial) { s.tourDone = false; if (s.tipsSeen != null) s.tipsSeen.clear(); } },
                    "Optional. A tour of headquarters on your first turn, and a tip the first time you reach each part of the game.");
            wrap(g, s.tutorial ? "On: a tour of headquarters on your first turn, plus a tip the first time you reach each new part of the game." : "Off. Switch it on for a tour of headquarters and tips along the way.", ax + 18, y + 14, aw - 36, F_XS, C_DIM);
            // column B: scenarios
            double bx = 538, bw = 520;
            u.panel(g, bx, ay, bw, 690, "Scenario");
            for (int i = 0; i < NSCEN; i++) {
                final int k = i;
                double cx = bx + 16 + (i % 2) * 248, cy = ay + 38 + (i / 2) * 214;
                boolean sel = s.scenario == i;
                boolean hov = u.card(g, cx, cy, 240, 204, sel, () -> s.scenario = k, null);
                scenarioArt(g, i, cx + 8, cy + 8, 224, 64, u.tsec, hov || sel);
                text(g, SCEN_NAME[i], cx + 12, cy + 92, F_H3, sel ? Color.white : C_TEXT);
                textFit(g, SCEN_TAG[i], cx + 12, cy + 108, 220, F_XSB, sel ? C_GOLD : C_DIM);
                wrap(g, SCEN_SHORT[i], cx + 12, cy + 126, 216, F_T, C_DIM);
                u.hit(cx, cy, 240, 204, () -> s.scenario = k, SCEN_NAME[i] + "\n" + SCEN_DESC[i]);
            }
            // column C: midterms and calendar
            double cx = 1078, cw = 474;
            u.panel(g, cx, ay, cw, 300, "The 2026 midterms (forecast - editable)");
            y = ay + 44;
            wrap(g, "Polling as of October 28, 2026: the generic ballot average is about D+8.7 and the president's approval about 37%. Set the results you expect; they shape the national mood and the Senate map.", cx + 18, y, cw - 36, F_XS, C_DIM);
            y += 58;
            text(g, "House seats won by Democrats", cx + 18, y + 16, F_S, C_TEXT);
            u.stepper(g, cx + cw - 18 - 128, y, String.valueOf(s.midHouseD), () -> s.midHouseD = Math.max(180, s.midHouseD - 1), () -> s.midHouseD = Math.min(280, s.midHouseD + 1), null, 74);
            text(g, s.midHouseD >= 218 ? "Democratic House" : "Republican House", cx + 18, y + 32, F_T, s.midHouseD >= 218 ? C_DEM_L : C_REP_L);
            y += 46;
            text(g, "Senate seats held by Democrats", cx + 18, y + 16, F_S, C_TEXT);
            u.stepper(g, cx + cw - 18 - 128, y, String.valueOf(s.midSenateD), () -> s.midSenateD = Math.max(42, s.midSenateD - 1), () -> s.midSenateD = Math.min(56, s.midSenateD + 1), null, 74);
            text(g, s.midSenateD >= 51 ? "Democratic Senate" : s.midSenateD == 50 ? "50-50: the vice president breaks ties" : "Republican Senate", cx + 18, y + 32, F_T, s.midSenateD >= 51 ? C_DEM_L : C_DIM);
            y += 46;
            text(g, "National House vote", cx + 18, y + 14, F_S, C_TEXT);
            textR(g, margin(s.midGeneric), cx + cw - 18, y + 14, F_BB, s.midGeneric >= 0 ? C_DEM_L : C_REP_L);
            u.slider(g, cx + 18, y + 34, cw - 36, (s.midGeneric + 6) / 20.0, s.midGeneric >= 0 ? C_DEM : C_REP, v -> s.midGeneric = Math.round((v * 20 - 6) * 2) / 2.0, null);
            u.button(g, cx + cw - 128, ay + 12, 110, 22, "Reset forecast", B_SUBTLE, () -> { s.midHouseD = MID_HOUSE_D; s.midSenateD = MID_SENATE_D; s.midGeneric = MID_GENERIC; });
            double py = ay + 312;
            u.panel(g, cx, py, cw, 378, "Primary calendar - early states");
            if (s.mode != MODE_FULL) {
                wrap(g, "Not used in a general-election-only game.", cx + 18, py + 48, cw - 36, F_S, C_DIM);
            } else {
                wrap(g, "The DNC adopted this early-state order in August 2026. Reorder or swap states for a what-if - click a state to change it.", cx + 18, py + 44, cw - 36, F_XS, C_DIM);
                earlyList(u, g, s.demEarly, Setup.DEM_EARLY_DATES, "Democrats", cx + 18, py + 82, C_DEM_L);
                earlyList(u, g, s.repEarly, Setup.REP_EARLY_DATES, "Republicans", cx + 248, py + 82, C_REP_L);
            }
            // footer
            u.button(g, 48, 832, 150, 44, "Back", B_GHOST, () -> u.go(SC_HOME));
            u.button(g, DW - 48 - 300, 832, 300, 44, "Choose your candidate", B_GOLD, () -> { pickSel = -1; u.go(SC_PICK); });
        }

        static void earlyList(Battleground2028 u, Graphics2D g, int[] arr, String[] dates, String title, double x, double y, Color col) {
            text(g, title, x, y, F_H3, col);
            for (int i = 0; i < arr.length; i++) {
                final int k = i;
                double ry = y + 12 + i * 44;
                int[] d = dateOf(Setup.weekOfStr(dates[i]) * 7 + 1);
                String dt = dates[i].substring(5);
                text(g, (i + 1) + ".", x, ry + 22, F_SB, C_DIM);
                u.button(g, x + 20, ry + 6, 110, 26, arr[i] >= 0 ? STATES[arr[i]].name : "-", B_NORM, () -> {
                    String[] names = new String[NS];
                    for (int s = 0; s < NS; s++) names[s] = STATES[s].name;
                    Popup p = u.openPopup(u.mx, u.my, 200, "Choose a state", names, s -> { for (int j = 0; j < arr.length; j++) if (arr[j] == s) arr[j] = arr[k]; arr[k] = s; });
                    p.y = 120;
                }, "Click to choose a different state");
                text(g, MONTHS[Integer.parseInt(dt.substring(0, 2)) - 1] + " " + Integer.parseInt(dt.substring(3)), x + 136, ry + 24, F_XS, C_DIM);
                u.iconButton(g, x + 186, ry + 5, 20, 14, IC_UP, i > 0 ? () -> { int t = arr[k - 1]; arr[k - 1] = arr[k]; arr[k] = t; } : null, "Move earlier");
                u.iconButton(g, x + 186, ry + 21, 20, 14, IC_DOWN, i < arr.length - 1 ? () -> { int t = arr[k + 1]; arr[k + 1] = arr[k]; arr[k] = t; } : null, "Move later");
            }
        }

        /** Small illustrative art for each scenario card. */
        static void scenarioArt(Graphics2D g, int sc, double x, double y, double w, double h, double t, boolean live) {
            RoundRectangle2D rr = new RoundRectangle2D.Double(x, y, w, h, 8, 8);
            Color[] tops = {rgb(0x1d3b75), rgb(0x5a1d2a), rgb(0x3b1d12), rgb(0x1d2f3b), rgb(0x1d4a2e), rgb(0x153d4a)};
            g.setPaint(new GradientPaint((float) x, (float) y, tops[sc], (float) x, (float) (y + h), shade(tops[sc], 0.4)));
            g.fill(rr);
            Shape pc = g.getClip();
            g.clip(rr);
            double tt = live ? t : 0;
            switch (sc) {
                case SC_BASE: {
                    for (int i = 0; i < 24; i++) {
                        double v = Math.sin(i * 0.9 + tt * 1.5) * 0.5 + 0.5;
                        g.setColor(i % 2 == 0 ? alpha(C_DEM, 0.8) : alpha(C_REP, 0.8));
                        double bh = 10 + v * (h - 20);
                        g.fill(new Rectangle2D.Double(x + 8 + i * 9, y + h - bh, 6, bh));
                    }
                    break;
                }
                case SC_SCANDAL: {
                    for (int i = 0; i < 7; i++) {
                        double px = x + 20 + i * 30, py = y + 14 + (i % 2) * 18;
                        g.setColor(new Color(240, 236, 220, 200));
                        g.fill(new Rectangle2D.Double(px, py, 24, 30));
                        g.setColor(new Color(60, 60, 60, 160));
                        for (int l = 0; l < 4; l++) g.fill(new Rectangle2D.Double(px + 3, py + 5 + l * 6, l == 2 ? 10 : 18, 2));
                        g.setColor(new Color(200, 30, 40, 200));
                        g.fill(new Rectangle2D.Double(px + 3, py + 17, 18, 3));
                    }
                    break;
                }
                case SC_WAR: {
                    g.setColor(new Color(255, 120, 40, 60));
                    for (int i = 0; i < 6; i++) {
                        double fx = x + 30 + i * 36, r = 10 + 6 * Math.sin(tt * 3 + i);
                        g.fill(new Ellipse2D.Double(fx - r, y + h - 22 - r, r * 2, r * 2));
                    }
                    g.setColor(new Color(200, 220, 255, 160));
                    for (int i = 0; i < 4; i++) {
                        double jx = x + ((tt * 60 + i * 70) % (w + 40)) - 20;
                        Path2D jet = new Path2D.Double();
                        jet.moveTo(jx, y + 18 + i * 8); jet.lineTo(jx - 14, y + 14 + i * 8); jet.lineTo(jx - 14, y + 22 + i * 8); jet.closePath();
                        g.fill(jet);
                    }
                    break;
                }
                case SC_AIREC: {
                    Path2D p = new Path2D.Double();
                    for (int i = 0; i <= 20; i++) {
                        double px = x + 10 + i * (w - 20) / 20, py = y + 14 + i * 2.2 + Math.sin(i + tt * 2) * 4;
                        if (i == 0) p.moveTo(px, py); else p.lineTo(px, py);
                    }
                    g.setColor(C_BAD);
                    g.setStroke(new BasicStroke(2.5f));
                    g.draw(p);
                    g.setStroke(new BasicStroke(1f));
                    g.setColor(new Color(124, 196, 255, 120));
                    for (int i = 0; i < 5; i++) g.fill(new RoundRectangle2D.Double(x + 30 + i * 36, y + h - 18, 22, 12, 3, 3));
                    break;
                }
                case SC_BOOM: {
                    Path2D p = new Path2D.Double();
                    for (int i = 0; i <= 20; i++) {
                        double px = x + 10 + i * (w - 20) / 20, py = y + h - 10 - i * 2.2 - Math.sin(i + tt * 2) * 3;
                        if (i == 0) p.moveTo(px, py); else p.lineTo(px, py);
                    }
                    g.setColor(C_GOOD);
                    g.setStroke(new BasicStroke(2.5f));
                    g.draw(p);
                    g.setStroke(new BasicStroke(1f));
                    break;
                }
                default: {
                    for (int i = 0; i < 3; i++) {
                        Color c = i == 0 ? C_DEM : i == 1 ? rgb(0x22c3d6) : C_REP;
                        double v = i == 1 ? 0.6 + 0.2 * Math.sin(tt * 2) : 0.75;
                        g.setColor(alpha(c, 0.85));
                        g.fill(new RoundRectangle2D.Double(x + 40 + i * 56, y + h - 8 - v * (h - 20), 40, v * (h - 20), 6, 6));
                    }
                }
            }
            g.setClip(pc);
        }

        // ---------------------------------------------------------------- CANDIDATE SELECTION
        static void pick(Battleground2028 u, Graphics2D g) {
            Settings s = u.setup;
            header(u, g, "Choose Your Candidate", pickParty == IND ? "Run as an independent: no party, no primaries - just you, the ballot and 270." : s.mode == MODE_FULL ? "Twenty-one Democrats and fourteen Republicans are weighing 2028 runs. Pick one - or build your own." : "Choose your nominee, then your opponent.");
            u.segmented(g, DW - 48 - 480, 40, 480, 38, new String[]{"Democrats", "Republicans", "Independent"}, pickParty == IND ? 2 : pickParty, i -> { pickParty = i == 2 ? IND : i; pickSel = -1; u.resetScroll("pick"); }, null);
            if (pickParty == IND) { independentPick(u, g); return; }
            List<Cand> list = partyList(pickParty);
            if (pickSel < 0 || pickSel >= list.size()) pickSel = 0;
            // the grid
            double gx = 48, gy = 118, gw = 600, gh = 700;
            double off = u.beginScroll(g, "pick", gx, gy, gw, gh);
            int h0 = u.hits.size();
            double cw = 194, ch = 92;
            for (int i = 0; i <= list.size(); i++) {
                final int k = i;
                double x = gx + (i % 3) * (cw + 8), y = gy + (i / 3) * (ch + 8) - off;
                if (i == list.size()) {
                    boolean hov = u.card(g, x, y, cw, ch, false, () -> { Creator.start(u, pickParty); u.go(SC_CREATE); }, "Design your own candidate");
                    textC(g, "+", x + 36, y + 58, new Font(SANS, Font.PLAIN, 40), hov ? C_GOLD : C_DIM);
                    text(g, "Create your own", x + 68, y + 40, F_BB, hov ? Color.white : C_TEXT);
                    text(g, "Sandbox candidate", x + 68, y + 58, F_XS, C_DIM);
                    continue;
                }
                Cand c = list.get(i);
                boolean sel = i == pickSel;
                u.card(g, x, y, cw, ch, sel, () -> { pickSel = k; snd(Sfx.HOVER, 0.6, 1.4); }, null);
                Portrait.draw(g, c, x + 6, y + 6, 66, 80, 0, 0, true, true);
                textFit(g, c.name(), x + 80, y + 24, cw - 86, F_SB, sel ? Color.white : C_TEXT);
                g.setClip(new Rectangle2D.Double(x + 78, y, cw - 82, ch));
                textFit(g, c.role, x + 80, y + 40, cw - 88, F_T, C_DIM);
                g.setClip(new Rectangle2D.Double(gx, gy, gw, gh));
                chip(g, shortFaction(c.faction), x + 80, y + 50, partyColor(c.party), false);
                if (s.mode == MODE_FULL) text(g, "Polling " + Math.round(c.poll) + "%", x + 80, y + 82, F_T, C_FAINT);
            }
            u.clipHits(h0, gy, gy + gh);
            u.endScroll(g, "pick", ((list.size() + 1 + 2) / 3) * (ch + 8));
            // detail
            Cand c = list.get(pickSel);
            detail(u, g, c, 668, 118, DW - 48 - 668, 700, s);
            u.button(g, 48, 832, 150, 44, "Back", B_GHOST, () -> u.go(SC_SETUP));
            u.button(g, DW - 48 - 620, 832, 150, 44, "Edit platform", B_NORM, () -> PlatformEd.open(u, c, SC_PICK));
            String run = "Run as " + c.name();
            if (s.mode == MODE_GENERAL) {
                List<Cand> opp = partyList(c.party == DEM ? REP : DEM);
                if (oppSel < 0 || oppSel >= opp.size() || opp.get(oppSel).party == c.party) { oppSel = 0; for (int i = 0; i < opp.size(); i++) if (opp.get(i) == Setup.likelyNominee(opp.get(0).party)) oppSel = i; }
                Cand oc = opp.get(oppSel);
                text(g, "Opponent", DW - 48 - 452, 850, F_TB, C_DIM);
                u.button(g, DW - 48 - 452, 856, 132, 24, oc.name(), B_NORM, () -> {
                    String[] names = new String[opp.size()];
                    for (int i = 0; i < opp.size(); i++) names[i] = opp.get(i).name();
                    u.openPopup(u.mx - 100, 300, 230, "Opponent", names, i -> oppSel = i);
                });
            }
            u.button(g, DW - 48 - 300, 832, 300, 44, run, B_GOLD, () -> { if (s.mode == MODE_FULL) FieldScreen.open(u, c, false); else startGame(u, c); });
        }

        /** The independent tab: a created candidate and the start of their campaign. */
        static void independentPick(Battleground2028 u, Graphics2D g) {
            double x = 48, y = 118, w = DW - 96;
            u.panel(g, x, y, w, 700, null);
            boolean hov = u.card(g, x + 30, y + 30, 360, 300, false, () -> { Creator.start(u, IND); u.go(SC_CREATE); }, "Design your own independent candidate");
            textC(g, "+", x + 210, y + 170, new Font(SANS, Font.PLAIN, 90), hov ? C_IND : C_DIM);
            textC(g, "Create an independent", x + 210, y + 250, F_H2, hov ? Color.white : C_TEXT);
            textC(g, "Sandbox candidate - any ideology", x + 210, y + 274, F_XS, C_DIM);
            double tx = x + 430, tw = w - 470;
            text(g, "Running outside the two parties", tx, y + 60, F_H1, C_IND);
            String[] para = {
                    "No primaries: you skip the party fights, but you start with no party machine, no donor network handed to you and no automatic place on the ballot.",
                    "Two ways to start. LABOR DAY: jump straight into the general election against the two nominees (you choose them), already on the ballot everywhere. THE LONG CAMPAIGN: enter in January 2028 as the primaries begin, while the parties' races play out without you, and earn your place on the ballot state by state - signatures, money and filing deadlines from March (North Carolina) to September.",
                    "The math is brutal. Voters don't want to waste a vote, so your support grows with your name recognition and your standing in the polls. Reach 15% in the national polls to make the debate stage. Win states outright - or deny anyone 270 and send the election to the House.",
                    "Best played in the Third Force scenario, where a billionaire's backing and an anti-system mood are on your side - you take the place of the America Party candidate."};
            double yy = y + 96;
            for (String p : para) yy += wrap(g, p, tx, yy, tw, F_B, C_TEXT) + 12;
            u.button(g, 48, 832, 150, 44, "Back", B_GHOST, () -> u.go(SC_SETUP));
            u.button(g, DW - 48 - 300, 832, 300, 44, "Create your independent", B_GOLD, () -> { Creator.start(u, IND); u.go(SC_CREATE); });
        }

        /** An independent chooses how the campaign begins. */
        static void independentStart(Battleground2028 u, Cand c) {
            Settings s = u.setup;
            Modal m = new Modal();
            m.title = "When does your campaign begin?";
            m.body = c.name() + " is running as an independent.";
            m.stacked = true;
            m.col = C_IND;
            m.face = c;
            m.w = 760;
            m.buttons = new String[]{"Labor Day 2028", "January 2028: the long campaign"};
            m.descs = new String[]{"The general election only. You're on the ballot everywhere; pick the two nominees you'll face. About an hour.",
                    "Enter as the primaries begin. The parties' races play out without you while you raise money, build name recognition and earn ballot access state by state. Two to three hours."};
            m.actions = new Runnable[]{() -> { s.mode = MODE_GENERAL; FieldScreen.open(u, c, true); }, () -> { s.mode = MODE_FULL; FieldScreen.open(u, c, false); }};
            u.modal = m;
        }

        static String shortFaction(int f) { return FACTIONS[f].name; }

        static void startGame(Battleground2028 u, Cand c) {
            Settings s = u.setup;
            s.party = c.party;
            if (c.custom) { s.custom = c; s.candId = c.id; } else { s.custom = null; s.candId = c.id; }
            if (s.mode == MODE_GENERAL && c.party != IND) {
                List<Cand> opp = partyList(c.party == DEM ? REP : DEM);
                s.opponentId = opp.get(clampi(oppSel, 0, opp.size() - 1)).id;
            }
            if (c.party == IND && s.mode == MODE_GENERAL) {
                if (s.lockD == null) s.lockD = Setup.likelyNominee(DEM).id;
                if (s.lockR == null) s.lockR = Setup.likelyNominee(REP).id;
            }
            s.seed = System.nanoTime();
            if (c.party == IND && s.mode == MODE_FULL) { Loading.start(u, s); return; }
            finishStart(u, Setup.newGame(s));
        }

        static void finishStart(Battleground2028 u, Game g) {
            Settings s = g.set;
            Camp me = g.me();
            Cand c = me != null ? me.c : null;
            if (s.platform != null && me != null && c != null && s.platformFor != null && s.platformFor.equals(c.id)) {
                me.plat = s.platform.clone();
                me.enthusiasm = Vote.baseEnthusiasm(me);
            }
            if (s.difficulty == 0 && me != null) { me.cash *= 1.25; me.smallList *= 1.15; }
            if (s.difficulty == 2 && me != null) { me.cash *= 0.85; }
            u.game = g;
            Hq.begin(u);
            u.go(SC_HQ);
            snd(Sfx.STING);
        }

        static Color ratingColor(int v, boolean inverse) {
            int x = inverse ? 100 - v : v;
            return x >= 80 ? C_GOOD : x >= 65 ? C_ACCENT : x >= 45 ? C_AMBER : C_BAD;
        }

        /** The big candidate profile used on the selection page. */
        static void detail(Battleground2028 u, Graphics2D g, Cand c, double x, double y, double w, double h, Settings s) {
            u.panel(g, x, y, w, h, null);
            Portrait.draw(g, c, x + 18, y + 18, 220, 264, u.tsec, 0.18 + 0.18 * Math.sin(u.tsec * 0.8), false, true);
            double tx = x + 256, tw = w - 274;
            text(g, c.name(), tx, y + 50, F_DISPLAY, Color.white);
            text(g, c.role + "  |  " + (c.homeAb != null ? STATES[Math.max(0, stateIdx(c.homeAb))].name : "") + "  |  age " + (2028 - c.born) + " on Election Day", tx, y + 76, F_S, C_DIM);
            Faction f = FACTIONS[c.faction];
            double cwid = chip(g, f.name, tx, y + 88, partyColor(c.party), true);
            chip(g, PARTY[c.party], tx + cwid + 6, y + 88, partyLight(c.party), false);
            double yy = y + 124;
            yy += wrap(g, f.desc, tx, yy, tw, F_S, C_TEXT) + 4;
            yy += wrap(g, f.impact, tx, yy, tw, F_XS, C_DIM) + 8;
            // key numbers
            String[][] kv = {{"Opening poll", s.mode == MODE_FULL ? Math.round(c.poll) + "%" : "-"}, {"Name ID", Math.round(c.nameId * 100) + "%"},
                    {"Cash on hand", money(c.cash)}, {"Email/text list", compact(c.list)}, {"Can self-fund", c.selfMax > 0 ? money(c.selfMax) : "-"}};
            for (int i = 0; i < kv.length; i++) {
                double kx = tx + i * (tw / 5);
                label(g, kv[i][0], kx, yy + 10, C_DIM);
                text(g, kv[i][1], kx, yy + 32, F_NUM, Color.white);
            }
            yy += 48;
            // ratings
            double ry = y + 300, rx = x + 18, rw = 400;
            label(g, "Ratings (game estimates)", rx, ry, C_GOLD);
            for (int i = 0; i < NA; i++) {
                double bx = rx + (i % 2) * (rw / 2 + 6), by = ry + 12 + (i / 2) * 23;
                int v = c.a[i];
                boolean inv = i == A_SCA;
                textFit(g, ATTR[i], bx, by + 10, 120, F_T, C_TEXT);
                bar(g, bx + 122, by + 3, rw / 2 - 156, 7, v / 100.0, ratingColor(v, inv));
                textR(g, String.valueOf(v), bx + rw / 2 - 8, by + 11, F_TB, ratingColor(v, inv));
                u.hit(bx, by - 4, rw / 2, 22, null, ATTR[i] + " - " + v + "\n" + ATTR_DESC[i]);
            }
            // ideology map
            double mx0 = x + 440, my0 = y + 300, ms = 170;
            label(g, "Ideology", mx0, my0, C_GOLD);
            ideologyMap(g, c, mx0, my0 + 12, ms);
            // platform
            double px0 = mx0 + ms + 26, pw = x + w - 18 - px0;
            label(g, "Starting platform", px0, my0, C_GOLD);
            platformBars(g, u, c.plat, px0, my0 + 12, pw, 14.6);
            // strengths / weaknesses / record
            double sy = y + 596;
            double colW = (w - 54) / 3;
            label(g, "Strengths", x + 18, sy, C_GOOD);
            wrap(g, c.pros == null ? "" : c.pros, x + 18, sy + 16, colW, F_XS, C_TEXT);
            label(g, "Weaknesses", x + 30 + colW, sy, C_BAD);
            wrap(g, c.cons == null ? "" : c.cons, x + 30 + colW, sy + 16, colW, F_XS, C_TEXT);
            label(g, "Record to run on", x + 42 + colW * 2, sy, C_ACCENT);
            double ry2 = sy + 16;
            for (int i = 0; i < c.record.length && ry2 < y + h - 14; i++) ry2 += wrap(g, "- " + c.record[i], x + 42 + colW * 2, ry2, colW, F_XS, C_TEXT) + 2;
            if (c.bio != null) u.hit(x + 18, y + 18, 220, 264, null, c.bio);
        }

        static void ideologyMap(Graphics2D g, Cand c, double x, double y, double s) {
            g.setColor(new Color(0, 0, 0, 80));
            g.fill(new RoundRectangle2D.Double(x, y, s, s, 10, 10));
            g.setColor(new Color(255, 255, 255, 20));
            g.draw(new Line2D.Double(x + s / 2, y + 4, x + s / 2, y + s - 4));
            g.draw(new Line2D.Double(x + 4, y + s / 2, x + s - 4, y + s / 2));
            text(g, "econ. left", x + 4, y + s - 5, F_T, C_FAINT);
            textR(g, "econ. right", x + s - 4, y + s - 5, F_T, C_FAINT);
            text(g, "traditional", x + 4, y + 12, F_T, C_FAINT);
            for (int i = 0; i < NF; i++) {
                Faction f = FACTIONS[i];
                double fx = x + s / 2 + f.econ * (s / 2 - 12), fy = y + s / 2 - f.cult * (s / 2 - 12);
                g.setColor(alpha(partyColor(f.party), i == c.faction ? 1 : 0.45));
                double r = i == c.faction ? 7 : 4;
                g.fill(new Ellipse2D.Double(fx - r, fy - r, r * 2, r * 2));
                if (i == c.faction) {
                    g.setColor(Color.white);
                    g.setStroke(new BasicStroke(1.5f));
                    g.draw(new Ellipse2D.Double(fx - r, fy - r, r * 2, r * 2));
                    g.setStroke(new BasicStroke(1f));
                }
            }
            double cx = x + s / 2 + c.econ * (s / 2 - 12), cy = y + s / 2 - c.cult * (s / 2 - 12);
            g.setColor(C_GOLD);
            g.fill(new Ellipse2D.Double(cx - 3, cy - 3, 6, 6));
        }

        /** Issue positions as small bars with party-base markers. */
        static void platformBars(Graphics2D g, Battleground2028 u, double[] plat, double x, double y, double w, double rowH) {
            double lw = Math.min(150, w * 0.48), bw = w - lw - 4;
            for (int i = 0; i < NI; i++) {
                double ry = y + i * rowH;
                textFit(g, ISSUE[i], x, ry + 10, lw - 4, F_T, C_TEXT);
                double bx = x + lw;
                g.setColor(new Color(255, 255, 255, 14));
                g.fill(new RoundRectangle2D.Double(bx, ry + 3, bw, 7, 7, 7));
                g.setColor(new Color(255, 255, 255, 40));
                g.draw(new Line2D.Double(bx + bw / 2, ry + 1, bx + bw / 2, ry + 12));
                double dx = bx + bw / 2 + IDEAL_D[i] * bw / 2, rx = bx + bw / 2 + IDEAL_R[i] * bw / 2;
                g.setColor(alpha(C_DEM_L, 0.8)); g.fill(new Rectangle2D.Double(dx - 1, ry + 2, 2, 9));
                g.setColor(alpha(C_REP_L, 0.8)); g.fill(new Rectangle2D.Double(rx - 1, ry + 2, 2, 9));
                double px = bx + bw / 2 + plat[i] * bw / 2;
                g.setColor(C_GOLD);
                g.fill(new Ellipse2D.Double(px - 4.5, ry + 2, 9, 9));
                u.hit(x, ry, w, rowH, null, ISSUE[i] + "\n" + ISSUE_POLES[i][0] + "  <->  " + ISSUE_POLES[i][1] + "\nGold: this platform. Blue/red ticks: the typical Democratic and Republican voter.");
            }
        }

        // ---------------------------------------------------------------- BRIEFING
        static final String[] BRIEF_TOPICS = {"How to play", "The week", "Money & ads", "Field & volunteers", "Message, promises & debates", "Polls & the map", "Primaries & delegates", "Running as an independent", "The electorate model", "Sources & notes"};

        static void briefing(Battleground2028 u, Graphics2D g) {
            header(u, g, "Briefing", "Rules of the road, how the simulation works, and where the numbers come from.");
            for (int i = 0; i < BRIEF_TOPICS.length; i++) {
                final int k = i;
                u.button(g, 48, 124 + i * 44, 260, 36, BRIEF_TOPICS[i], i == briefTopic ? B_ON : B_SUBTLE, () -> { briefTopic = k; u.resetScroll("brief"); });
            }
            u.panel(g, 330, 118, DW - 378, 700, null);
            double off = u.beginScroll(g, "brief", 350, 128, DW - 410, 680);
            double y = 150 - off;
            text(g, BRIEF_TOPICS[briefTopic], 360, y, F_H1, Color.white);
            y += 30;
            for (String para : Briefing.TEXT[briefTopic]) {
                if (para.startsWith("#")) { y += 8; text(g, para.substring(1).trim(), 360, y + 4, F_H3, C_GOLD); y += 22; continue; }
                y += wrap(g, para, 360, y, DW - 440, F_B, C_TEXT) + 10;
            }
            u.endScroll(g, "brief", y + off - 128 + 20);
            u.button(g, 48, 832, 180, 44, "Back", B_GHOST, () -> u.go(briefReturn));
            if (briefReturn == SC_HQ && u.game != null && u.game.me() != null)
                u.button(g, 240, 832, 280, 44, "Take the guided tour", B_GOLD, () -> Tutorial.replay(u), "A step-by-step tour of headquarters, plus a tip the first time you reach each part of the game.");
        }

        // ---------------------------------------------------------------- RUNNING MATE
        static int vpSel = -1;

        static final Map<String, double[]> vpEffCache = new HashMap<String, double[]>();

        static double[] vpEff(Game game, Camp me, Cand c) {
            String key = c.id + "@" + game.week + "@" + System.identityHashCode(game);
            double[] v = vpEffCache.get(key);
            if (v == null) { if (vpEffCache.size() > 200) vpEffCache.clear(); v = Campaign.vpEffect(game, me, c); vpEffCache.put(key, v); }
            return v;
        }

        static List<Cand> vpPool(Game g, Camp me) {
            List<Cand> l = new ArrayList<Cand>();
            if (me.party == IND) return Primary.indVpPool(g, me);
            for (Cand c : ROSTER) if (c.party == me.party && c != me.c && !c.id.equals(me.c.id)) l.add(c);
            return l;
        }

        static void vp(Battleground2028 u, Graphics2D g) {
            Game game = u.game;
            Camp me = game.me();
            header(u, g, "Choose a Running Mate", me.party == IND ? "A unity ticket: recruit a Democrat or a Republican who isn't on their own party's ticket. Vetting reveals skeletons before the press does."
                    : "Balance the ticket, carry a state, or double down on your message. Vetting reveals skeletons before the press does.");
            List<Cand> pool = vpPool(game, me);
            if (pool.isEmpty()) { u.button(g, 48, 832, 180, 44, "Back", B_GHOST, () -> u.go(SC_HQ)); return; }
            if (vpSel < 0 || vpSel >= pool.size()) vpSel = 0;
            double gx = 48, gy = 118, gw = 600, gh = 700;
            double off = u.beginScroll(g, "vp", gx, gy, gw, gh);
            int h0 = u.hits.size();
            for (int i = 0; i < pool.size(); i++) {
                final int k = i;
                Cand c = pool.get(i);
                double x = gx + (i % 3) * 202, y = gy + (i / 3) * 100 - off;
                u.card(g, x, y, 194, 92, i == vpSel, () -> vpSel = k, null);
                Portrait.draw(g, c, x + 6, y + 6, 66, 80, 0, 0, true, true);
                textFit(g, c.name(), x + 80, y + 24, 108, F_SB, C_TEXT);
                text(g, c.homeAb + (me.party == IND ? "  " + PARTY_SHORT[c.party] : ""), x + 80, y + 42, F_T, me.party == IND ? partyLight(c.party) : C_DIM);
                double[] ef = vpEff(game, me, c);
                text(g, "Nat. " + signed1(ef[0]) + "  Home " + signed1(ef[1]), x + 80, y + 60, F_T, ef[0] >= 0 ? C_GOOD : C_BAD);
                if (me.vetted.contains(c.id)) chip(g, "VETTED", x + 80, y + 68, C_GOOD, false);
            }
            u.clipHits(h0, gy, gy + gh);
            u.endScroll(g, "vp", ((pool.size() + 2) / 3) * 100);
            Cand c = pool.get(vpSel);
            double x = 668, y = 118, w = DW - 48 - 668;
            u.panel(g, x, y, w, 700, null);
            Portrait.draw(g, c, x + 18, y + 18, 200, 240, u.tsec, 0.15, false, true);
            text(g, c.name(), x + 240, y + 52, F_DISPLAY, Color.white);
            text(g, c.role, x + 240, y + 78, F_S, C_DIM);
            double[] ef = vpEff(game, me, c);
            label(g, "Projected effect on the ticket", x + 240, y + 112, C_GOLD);
            text(g, "Nationally: " + signed1(ef[0]) + " pts", x + 240, y + 136, F_BB, ef[0] >= 0 ? C_GOOD : C_BAD);
            text(g, "In " + c.homeAb + ": " + signed1(ef[1]) + " pts", x + 440, y + 136, F_BB, ef[1] >= 0 ? C_GOOD : C_BAD);
            boolean vetted = me.vetted.contains(c.id);
            String risk = vetted ? (c.a[A_SCA] >= 60 ? "HIGH - the vetters found real problems" : c.a[A_SCA] >= 35 ? "MODERATE - some baggage" : "LOW - clean file") : "Unknown - vet to find out";
            text(g, "Scandal risk: " + risk, x + 240, y + 164, F_S, vetted ? (c.a[A_SCA] >= 60 ? C_BAD : c.a[A_SCA] >= 35 ? C_AMBER : C_GOOD) : C_DIM);
            String[] keys = {"Debating", "Crossover appeal", "Foreign-policy credibility", "Home-state strength", "Speeches", "Establishment ties"};
            int[] idx = {A_DEB, A_XOV, A_FPC, A_HOM, A_SPE, A_EST};
            for (int i = 0; i < keys.length; i++) {
                double bx = x + 240 + (i % 2) * 300, by = y + 192 + (i / 2) * 26;
                text(g, keys[i], bx, by + 10, F_XS, C_TEXT);
                bar(g, bx + 160, by + 3, 100, 7, c.a[idx[i]] / 100.0, ratingColor(c.a[idx[i]], false));
            }
            wrap(g, c.bio == null ? "" : c.bio, x + 18, y + 290, w - 36, F_S, C_TEXT);
            wrap(g, "Strengths: " + (c.pros == null ? "" : c.pros), x + 18, y + 370, w - 36, F_XS, C_DIM);
            wrap(g, "Weaknesses: " + (c.cons == null ? "" : c.cons), x + 18, y + 430, w - 36, F_XS, C_DIM);
            u.button(g, x + 18, y + 640, 220, 40, vetted ? "Vetted" : "Vet (" + money(Campaign.VET_COST) + ")", vetted ? B_OFF : B_NORM,
                    vetted ? null : () -> { if (!Campaign.vet(game, me, c)) u.toast("Not enough cash to vet.", C_BAD); });
            u.button(g, x + w - 18 - 300, y + 640, 300, 40, "Choose " + c.last, B_GOLD, () -> {
                game.setVp(me.party, c);
                game.log(NK_BIG, me.name() + " chooses " + c.name() + " as " + (me.c.look.female ? "her" : "his") + " running mate.");
                if (me.vpPromised != null) {
                    Cand pr = candById(me.vpPromised);
                    if (c.id.equals(me.vpPromised)) { me.enthusiasm += 0.02; game.log(NK_GOOD, "You keep your word to " + (pr != null ? pr.name() : "your former rival") + ". Their supporters are fired up."); }
                    else { me.enthusiasm -= 0.02; me.favor -= 0.004; game.log(NK_BAD, (pr != null ? pr.name() : "Your former rival") + "'s supporters feel betrayed: you promised serious consideration for the ticket."); }
                    me.vpPromised = null;
                }
                if (c.a[A_SCA] >= 60 && !vetted && game.rng.nextDouble() < 0.5) {
                    me.favor -= 0.02;
                    game.log(NK_BAD, "Reporters dig up damaging stories about " + c.name() + " that the campaign never vetted.");
                }
                me.enthusiasm += 0.03;
                game.dirty = true;
                u.go(SC_HQ);
                snd(Sfx.CHEER);
            });
            u.button(g, 48, 832, 180, 44, "Decide later", B_GHOST, () -> { u.go(SC_HQ); u.toast("Choose from the Campaign tab. If you don't, your staff picks at the convention.", C_AMBER); });
        }

        // ---------------------------------------------------------------- DEBATE NEGOTIATIONS
        static int[] negMine = {3, 1, 1, 0, 1, 0};     // count, timing, vp, host, muted mics, audience
        static int[] negDeal;
        static String negNote;

        static void negotiate(Battleground2028 u, Graphics2D g) {
            Game game = u.game;
            Camp me = game.me(), op = game.opp();
            header(u, g, "Debate Negotiations", "How many debates, when, and on whose terms. The side that's behind wants more; the side that's ahead wants fewer.");
            int[] ask = General.aiDebateAsk(game, op, me);
            double x = 48, y = 124, w = 740;
            u.panel(g, x, y, w, 560, "Your proposal");
            String[] timing = {"Early September", "Mid September", "Late September", "October"};
            String[] hosts = {"ABC News", "CBS News", "NBC News", "CNN", "Fox News", "NewsNation"};
            double ry = y + 50;
            text(g, "Presidential debates", x + 24, ry + 16, F_B, C_TEXT);
            u.segmented(g, x + 260, ry, 440, 30, new String[]{"None", "1", "2", "3", "4"}, negMine[0], i -> negMine[0] = i, null);
            ry += 54;
            text(g, "Timing of the first debate", x + 24, ry + 16, F_B, C_TEXT);
            u.segmented(g, x + 260, ry, 440, 30, timing, negMine[1], i -> negMine[1] = i, null);
            ry += 54;
            text(g, "Vice-presidential debate", x + 24, ry + 16, F_B, C_TEXT);
            u.segmented(g, x + 260, ry, 440, 30, new String[]{"No", "Yes"}, negMine[2], i -> negMine[2] = i, null);
            ry += 54;
            text(g, "Preferred host network", x + 24, ry + 16, F_B, C_TEXT);
            u.segmented(g, x + 260, ry, 440, 30, hosts, negMine[3], i -> negMine[3] = i, null);
            ry += 54;
            text(g, "Muted microphones", x + 24, ry + 16, F_B, C_TEXT);
            u.segmented(g, x + 260, ry, 440, 30, new String[]{"No", "Yes"}, negMine[4], i -> negMine[4] = i, null);
            ry += 54;
            text(g, "Live audience", x + 24, ry + 16, F_B, C_TEXT);
            u.segmented(g, x + 260, ry, 440, 30, new String[]{"No", "Yes"}, negMine[5], i -> negMine[5] = i, null);
            ry += 64;
            double lead = (me.party == DEM ? 1 : -1) * game.natPubAvg;
            wrap(g, "Leverage: you " + (lead > 1 ? "lead by " + fmt1(lead) + " - you can afford to hold firm and offer fewer debates." : lead < -1 ? "trail by " + fmt1(-lead) + " - you need the debates more than they do." : "are roughly tied - neither side has much leverage."),
                    x + 24, ry, w - 48, F_S, C_DIM);
            double ox = 812, ow = DW - 48 - ox;
            u.panel(g, ox, y, ow, 560, op.last() + " campaign wants");
            Portrait.draw(g, op.c, ox + 24, y + 44, 150, 180, u.tsec, 0, false, true);
            String[] lines = {ask[0] + " presidential debate" + (ask[0] == 1 ? "" : "s"), "First one: " + timing[clampi(ask[1], 0, 3)], ask[2] == 1 ? "A VP debate" : "No VP debate", "Host: " + hosts[clampi(ask[3], 0, 5)]};
            for (int i = 0; i < lines.length; i++) text(g, lines[i], ox + 196, y + 70 + i * 28, F_BB, C_TEXT);
            double theirLead = -lead;
            String why = theirLead > 1 ? "They lead by " + fmt1(theirLead) + " and want to run out the clock: fewer debates, later." : theirLead < -1 ? "They trail by " + fmt1(-theirLead) + " and want as many chances as possible to change the race." : "The race is close; both sides see risk and opportunity.";
            if (op.a(A_DEB) > me.a(A_DEB) + 8) why += " They also rate themselves the better debater.";
            else if (me.a(A_DEB) > op.a(A_DEB) + 8) why += " They know you're the stronger debater.";
            wrap(g, why, ox + 24, y + 240, ow - 48, F_S, C_DIM);
            if (negNote != null) wrap(g, negNote, ox + 24, y + 290, ow - 48, F_S, C_GOLD);
            if (negDeal != null) {
                label(g, "On the table", ox + 24, y + 340, C_DIM);
                text(g, negDeal[0] + " debates, starting " + timing[clampi(negDeal[1], 0, 3)].toLowerCase() + (negDeal[2] == 1 ? ", with a VP debate" : ", no VP debate") + ", " + hosts[clampi(negDeal[3], 0, 5)], ox + 24, y + 362, F_B, C_TEXT);
            }
            double by = 700;
            u.button(g, 48, by, 230, 44, "Propose a compromise", B_NORM, () -> {
                int[] d = General.negotiate(game, negMine, false);
                negDeal = d;
                negNote = "They counter with a middle ground. Accept it, or insist on your terms.";
            }, "Split the difference with their demands.");
            u.button(g, 290, by, 230, 44, "Insist on your terms", B_DANGER, () -> {
                int[] d = General.negotiate(game, negMine, true);
                if (d[4] > 0) { negDeal = d; negNote = "They blink. Your terms are on the table."; }
                else { negNote = "They walk out of the talks - and accuse you of ducking debates. Try again or accept their terms."; me.favor -= 0.005; }
            }, "Risky: they may accept, or walk away and attack you for it.");
            u.button(g, 532, by, 230, 44, "Accept their terms", B_NORM, () -> { negDeal = new int[]{ask[0], ask[1], ask[2], ask[3], 1}; negNote = "Their terms are on the table."; });
            u.button(g, DW - 48 - 280, by, 280, 44, negDeal != null ? "Sign the agreement" : "Sign (needs a deal)", negDeal != null ? B_GOLD : B_OFF, negDeal == null ? null : () -> {
                game.debateDeal = new int[]{negDeal[0], negDeal[1], negDeal[2], negDeal[3]};
                List<Debate> keep = new ArrayList<Debate>();
                for (Debate d : game.debates) if (d.party >= 0 || d.done) keep.add(d);
                game.debates = keep;
                General.setupDebates(game);
                for (Debate d : game.debates) if (d.party < 0 && !d.done) { d.mutedMics = negMine[4] == 1; d.audience = negMine[5] == 1; }
                if (negDeal[0] == 0) { me.favor -= 0.01; game.log(NK_BAD, "With no debates agreed, " + me.last() + " is accused of ducking the voters."); }
                game.debateTalks = 2;
                negDeal = null; negNote = null;
                u.go(SC_HQ);
            });
            u.button(g, 48, 832, 180, 44, "Back", B_GHOST, () -> u.go(SC_HQ));
        }
    }

    // =====================================================================
    //  24.  PLATFORM EDITOR AND THE SANDBOX CANDIDATE CREATOR
    // =====================================================================
    static final class PlatformEd {
        static int returnTo = SC_PICK;
        static Cand cand;
        static double[] plat, base;
        static final double RANGE = 0.35, BUDGET = 1.5;
        static double estGen, estPrim, estEnth, estEnth0;
        static String lastKey = "";

        static void open(Battleground2028 u, Cand c, int ret) {
            cand = c;
            returnTo = ret;
            base = c.plat.clone();
            Settings s = u.setup;
            plat = s.platform != null && c.id.equals(s.platformFor) ? s.platform.clone() : c.plat.clone();
            lastKey = "";
            u.go(SC_PLATFORM);
        }

        static double used() { double t = 0; for (int i = 0; i < NI; i++) t += Math.abs(plat[i] - base[i]); return t; }

        static void estimate() {
            String key = Arrays.toString(plat);
            if (key.equals(lastKey)) return;
            lastKey = key;
            Game tmp = new Game();
            tmp.set = new Settings();
            for (int i = 0; i < NI; i++) tmp.salience[i] = 1;
            Camp a = new Camp(cand, 0), b = new Camp(cand, 0);
            a.plat = plat.clone(); b.plat = base.clone();
            double gen = 0, w = 0, prim = 0, pw = 0;
            for (int s = 0; s < NS; s++) for (int c = 0; c < NCELL; c += 2) {
                double n = Elect.cvap[s][c] * Elect.turnB[s][c] * Elect.elast[s][c];
                gen += n * (Vote.issueTerm(tmp, a, s, c) - Vote.issueTerm(tmp, b, s, c));
                w += n;
                if (cand.party <= REP) {
                    double pwt = Vote.primaryWeight(cand.party, s, c);
                    double[] sl = Elect.sal[s][c];
                    double d = 0;
                    for (int i = 0; i < NI; i++) d -= sl[i] * (Math.abs(plat[i] - Vote.primaryIdealCached(cand.party, s, c, i)) - Math.abs(base[i] - Vote.primaryIdealCached(cand.party, s, c, i)));
                    prim += pwt * d * 3.2;
                    pw += pwt;
                }
            }
            estGen = gen / w * 50;                      // logit -> points of margin (approx.)
            estPrim = pw > 0 ? prim / pw * 25 : 0;      // utility -> points of primary share (approx.)
            estEnth = Vote.baseEnthusiasm(a);
            estEnth0 = Vote.baseEnthusiasm(b);
        }

        static void draw(Battleground2028 u, Graphics2D g) {
            Screens.header(u, g, "Platform: " + cand.name(), "Fine-tune the starting platform within the bounds of " + FACTIONS[cand.faction].name + " politics. Every move has a constituency - and a cost.");
            if (Elect.ready) estimate();
            double x = 48, y = 118, w = 1000;
            u.panel(g, x, y, w, 700, null);
            double rowH = 37;
            for (int i = 0; i < NI; i++) {
                final int k = i;
                double ry = y + 20 + i * rowH;
                text(g, ISSUE[i], x + 20, ry + 14, F_SB, C_TEXT);
                text(g, ISSUE_POLES[i][0], x + 20, ry + 28, F_T, C_FAINT);
                double sx = x + 300, sw = 520;
                textR(g, ISSUE_POLES[i][1], x + w - 20, ry + 28, F_T, C_FAINT);
                // allowed band
                double lo = Math.max(-1, base[i] - RANGE), hi = Math.min(1, base[i] + RANGE);
                g.setColor(new Color(255, 255, 255, 12));
                g.fill(new RoundRectangle2D.Double(sx, ry + 8, sw, 8, 8, 8));
                g.setColor(new Color(124, 196, 255, 40));
                g.fill(new RoundRectangle2D.Double(sx + (lo + 1) / 2 * sw, ry + 6, (hi - lo) / 2 * sw, 12, 6, 6));
                double dx = sx + (IDEAL_D[i] + 1) / 2 * sw, rx = sx + (IDEAL_R[i] + 1) / 2 * sw, bx = sx + (base[i] + 1) / 2 * sw;
                g.setColor(alpha(C_DEM_L, 0.9)); g.fill(new Rectangle2D.Double(dx - 1, ry + 3, 2, 18));
                g.setColor(alpha(C_REP_L, 0.9)); g.fill(new Rectangle2D.Double(rx - 1, ry + 3, 2, 18));
                g.setColor(new Color(255, 255, 255, 90)); g.fill(new Rectangle2D.Double(bx - 1, ry + 5, 2, 14));
                final double flo = lo, fhi = hi;
                u.slider(g, sx, ry + 12, sw, (plat[i] + 1) / 2, C_GOLD, v -> {
                    double nv = clamp(v * 2 - 1, flo, fhi);
                    double old = plat[k];
                    plat[k] = Math.round(nv * 20) / 20.0;
                    if (used() > BUDGET + 1e-9) plat[k] = old;
                }, ISSUE[i] + "\nBlue/red ticks: typical Democratic and Republican voters. White tick: your starting position. The shaded band is what your ideology allows.", false);
                double ds = plat[i] - base[i];
                if (Math.abs(ds) > 0.01) textR(g, (ds > 0 ? "+" : "") + fmt1(ds * 10), x + w - 20, ry + 14, F_TB, C_GOLD);
            }
            double px = 1068, pw = DW - 48 - px;
            u.panel(g, px, y, pw, 700, "Projected effects");
            Portrait.draw(g, cand, px + pw / 2 - 90, y + 40, 180, 216, u.tsec, 0, false, true);
            double ey = y + 290;
            double usedV = used();
            label(g, "Platform edits used", px + 20, ey, C_DIM);
            bar(g, px + 20, ey + 10, pw - 40, 8, usedV / BUDGET, usedV > BUDGET * 0.9 ? C_AMBER : C_ACCENT);
            textR(g, fmt1(usedV * 10) + " / " + fmt1(BUDGET * 10), px + pw - 20, ey, F_TB, C_TEXT);
            ey += 50;
            kv(g, "General electorate", (estGen >= 0 ? "+" : "") + fmt1(estGen) + " pts", estGen >= 0 ? C_GOOD : C_BAD, px + 20, ey, pw - 40);
            ey += 40;
            if (cand.party <= REP) {
                kv(g, "Primary voters", (estPrim >= 0 ? "+" : "") + fmt1(estPrim) + " pts", estPrim >= 0 ? C_GOOD : C_BAD, px + 20, ey, pw - 40);
                ey += 40;
            }
            kv(g, "Base enthusiasm", Math.round(estEnth * 100) + "%  (was " + Math.round(estEnth0 * 100) + "%)", estEnth >= estEnth0 - 0.001 ? C_GOOD : C_AMBER, px + 20, ey, pw - 40);
            ey += 50;
            wrap(g, "Moving to the middle wins swing voters but cools activists - fewer volunteers and small donors - and costs you in the primaries. Moving to the base does the reverse.", px + 20, ey, pw - 40, F_XS, C_DIM);
            u.button(g, 48, 832, 150, 44, "Back", B_GHOST, () -> u.go(returnTo));
            u.button(g, 210, 832, 150, 44, "Reset", B_NORM, () -> plat = base.clone());
            u.button(g, DW - 48 - 300, 832, 300, 44, "Save platform", B_GOLD, () -> {
                u.setup.platform = plat.clone();
                u.setup.platformFor = cand.id;
                if (cand.custom) cand.plat = plat.clone();
                u.go(returnTo);
                u.toast("Platform saved for " + cand.name() + ".", C_GOOD);
            });
        }

        static void kv(Graphics2D g, String k, String v, Color c, double x, double y, double w) {
            text(g, k, x, y, F_S, C_TEXT);
            textR(g, v, x + w, y, F_BB, c);
            g.setColor(new Color(255, 255, 255, 14));
            g.draw(new Line2D.Double(x, y + 10, x + w, y + 10));
        }
    }

    static final class Creator {
        static Cand c;
        static boolean god;
        static final int BUDGET = 1380, MIN = 20, MAX = 95;
        static final String[] ARCH = {"Governor", "Senator", "House firebrand", "Business outsider", "Celebrity", "General"};
        static final String[] ARCH_DESC = {"Executive record and a home-state base.", "Policy depth and establishment ties.", "Base fire, small dollars and viral reach.",
                "Self-funding and crossover appeal; thin on policy.", "Huge name ID and charisma; untested.", "Commander-in-chief credibility and discipline."};
        static final String[] ARCH_ATTR = {
                "70 60 70 68 72 72 72 55 70 10 60 55 68 75 30 85 50 60 70 80 60",
                "66 65 72 72 82 70 58 60 75 5 55 55 62 70 30 45 72 50 80 60 60",
                "82 78 80 74 68 55 60 90 35 0 88 92 75 80 30 20 35 30 30 55 92",
                "68 70 60 60 60 55 55 45 75 90 50 70 65 75 45 70 40 65 30 45 50",
                "88 85 75 62 55 50 70 75 50 60 70 92 70 70 50 15 30 60 25 50 65",
                "66 55 64 62 75 82 60 50 62 10 60 50 76 80 20 70 92 66 55 55 55"};
        static int arch = 0, field = 0;

        static void start(Battleground2028 u, int party) {
            c = new Cand();
            c.id = "custom";
            c.custom = true;
            c.first = "Alex"; c.last = "Morgan"; c.party = party;
            c.role = "Governor";
            c.homeAb = "PA"; c.home = stateIdx("PA");
            c.born = 1972;
            c.faction = party == DEM ? F_MOD : party == REP ? F_GOV : F_AMP;
            c.look = Look.parse("M 1 2 2 0.1 0 0 1.0 0.5 0 " + (party == DEM ? 0 : party == REP ? 1 : 3) + " 0.45 0.65");
            applyArch(party == IND ? 3 : 0);
            god = u.setup.godMode;
        }

        static void applyArch(int a) {
            arch = a;
            String[] t = ARCH_ATTR[a].split(" ");
            for (int i = 0; i < NA; i++) c.a[i] = Integer.parseInt(t[i]);
            c.role = a == 0 ? "Governor of " + STATES[Math.max(0, c.home)].name : a == 1 ? "U.S. Senator from " + STATES[Math.max(0, c.home)].name
                    : a == 2 ? "U.S. Representative" : a == 3 ? "Businessperson" : a == 4 ? "Entertainer and media personality" : "Retired four-star general";
            finish();
        }

        static int cost() {
            int t = 0;
            for (int i = 0; i < NA; i++) t += i == A_SCA ? 100 - c.a[i] : c.a[i];
            return t;
        }

        /** Derives money, name ID and the rest from the ratings. */
        static void finish() {
            c.econ = FACTIONS[c.faction].econ; c.cult = FACTIONS[c.faction].cult;
            c.plat = FACTIONS[c.faction].pos.clone();
            c.nameId = clamp(0.25 + c.a[A_VIR] / 300.0 + c.a[A_EST] / 400.0 + (arch == 4 ? 0.35 : 0), 0.2, 0.95);
            c.poll = Math.max(1, Math.round(c.nameId * 4));
            c.cash = (2 + c.a[A_BIG] / 10.0) * 1e6;
            c.list = (0.3 + c.a[A_SMD] / 25.0) * 1e6;
            c.selfMax = c.a[A_SELF] >= 30 ? Math.pow(c.a[A_SELF] / 100.0, 3) * 2000e6 : 0;
            c.home = stateIdx(c.homeAb);
            c.apG = new int[0]; c.apV = new double[0];
            c.record = new String[]{ARCH_DESC[arch]}; c.recIssue = new int[]{-1}; c.recCred = new double[]{0};
            c.bio = c.first + " " + c.last + ": " + c.role + ". A " + FACTIONS[c.faction].name.toLowerCase() + " " + (c.party == DEM ? "Democrat" : c.party == REP ? "Republican" : "independent") + " from " + STATES[Math.max(0, c.home)].name + ".";
            Integer[] idx = new Integer[NA];
            for (int i = 0; i < NA; i++) idx[i] = i;
            final int[] a = c.a;
            Arrays.sort(idx, (p, q) -> (q == A_SCA ? 100 - a[q] : a[q]) - (p == A_SCA ? 100 - a[p] : a[p]));
            c.pros = "Best at: " + ATTR[idx[0]].toLowerCase() + ", " + ATTR[idx[1]].toLowerCase() + " and " + ATTR[idx[2]].toLowerCase() + ".";
            c.cons = "Weakest at: " + ATTR[idx[NA - 1]].toLowerCase() + " and " + ATTR[idx[NA - 2]].toLowerCase() + ".";
        }

        static void draw(Battleground2028 u, Graphics2D g) {
            Screens.header(u, g, "Create a Candidate", "Sandbox: design a contender from scratch. Point-buy keeps it fair; unlimited mode lets you build anyone.");
            Look L = c.look;
            // appearance
            double x = 48, y = 118;
            u.panel(g, x, y, 380, 700, "Appearance");
            Portrait.draw(g, c, x + 90, y + 34, 200, 240, u.tsec, 0.2 + 0.2 * Math.sin(u.tsec), false, true);
            double ay = y + 292;
            u.segmented(g, x + 20, ay, 340, 28, new String[]{"Man", "Woman"}, L.female ? 1 : 0, i -> { L.female = i == 1; if (L.female && L.style < 6) L.style = 7; if (!L.female && L.style >= 6 && L.style <= 9) L.style = 2; if (L.female) L.facial = 0; Portrait.cache.clear(); }, null);
            ay += 40;
            ay = cycle(u, g, "Skin tone", L.skin, 8, v -> L.skin = v, x + 20, ay);
            ay = cycle(u, g, "Hair colour", L.hair, 8, v -> L.hair = v, x + 20, ay);
            ay = cycle(u, g, "Hairstyle", L.style, 12, v -> L.style = v, x + 20, ay);
            ay = cycle(u, g, "Facial hair", L.facial, 6, v -> L.facial = v, x + 20, ay);
            ay = cycle(u, g, "Glasses", L.glasses, 3, v -> L.glasses = v, x + 20, ay);
            ay = cycle(u, g, "Outfit", L.suit, 6, v -> L.suit = v, x + 20, ay);
            ay = cycle(u, g, L.female ? "Blouse" : "Tie", L.tie, 10, v -> L.tie = v, x + 20, ay);
            text(g, "Age", x + 20, ay + 14, F_XS, C_TEXT);
            u.slider(g, x + 120, ay + 10, 240, L.age, C_ACCENT, v -> L.age = v, null);
            ay += 28;
            text(g, "Grey", x + 20, ay + 14, F_XS, C_TEXT);
            u.slider(g, x + 120, ay + 10, 240, L.gray, C_ACCENT, v -> L.gray = v, null);
            ay += 28;
            text(g, "Smile", x + 20, ay + 14, F_XS, C_TEXT);
            u.slider(g, x + 120, ay + 10, 240, L.smile, C_ACCENT, v -> L.smile = v, null);
            // identity
            double ix = 444, iw = 500;
            u.panel(g, ix, y, iw, 700, "Identity");
            double iy = y + 40;
            text(g, "First name", ix + 20, iy + 4, F_XS, C_DIM);
            u.textField(g, "first", ix + 20, iy + 10, 220, 32, c.first, v -> { c.first = v.isEmpty() ? "?" : v; finish(); });
            text(g, "Last name", ix + 260, iy + 4, F_XS, C_DIM);
            u.textField(g, "last", ix + 260, iy + 10, 220, 32, c.last, v -> { c.last = v.isEmpty() ? "?" : v; finish(); });
            iy += 58;
            text(g, "Party", ix + 20, iy + 4, F_XS, C_DIM);
            u.segmented(g, ix + 20, iy + 10, 220, 30, new String[]{"Dem", "Rep", "Indep."}, c.party == IND ? 2 : c.party, i -> { c.party = i == 2 ? IND : i; c.faction = i == 0 ? F_MOD : i == 1 ? F_GOV : F_AMP; finish(); },
                    new String[]{"Democrat: run in the Democratic primaries.", "Republican: run in the Republican primaries.", "Independent: no primaries; earn ballot access and win as a third force."});
            text(g, "Home state", ix + 260, iy + 4, F_XS, C_DIM);
            u.button(g, ix + 260, iy + 10, 220, 30, STATES[Math.max(0, c.home)].name, B_NORM, () -> {
                String[] names = new String[NS];
                for (int s = 0; s < NS; s++) names[s] = STATES[s].name;
                Popup p = u.openPopup(u.mx, 120, 200, "Home state", names, s -> { c.homeAb = STATES[s].ab; c.home = s; applyArchRole(); finish(); });
                p.y = 110;
            });
            iy += 56;
            text(g, "Born", ix + 20, iy + 4, F_XS, C_DIM);
            u.stepper(g, ix + 20, iy + 10, String.valueOf(c.born), () -> c.born = Math.max(1940, c.born - 1), () -> c.born = Math.min(1993, c.born + 1), "Must be 35 by Inauguration Day 2029.", 70);
            text(g, "Age on Election Day: " + (2028 - c.born), ix + 160, iy + 27, F_XS, C_DIM);
            iy += 54;
            text(g, "Background", ix + 20, iy + 4, F_XS, C_DIM);
            for (int i = 0; i < ARCH.length; i++) {
                final int k = i;
                u.button(g, ix + 20 + (i % 3) * 156, iy + 10 + (i / 3) * 36, 150, 30, ARCH[i], arch == i ? B_TABON : B_NORM, () -> applyArch(k), ARCH_DESC[i] + "\nResets the ratings to a typical profile.");
            }
            iy += 88;
            text(g, "Ideology", ix + 20, iy + 4, F_XS, C_DIM);
            int n = 0;
            boolean anyIdeology = c.party == IND;
            int cols = anyIdeology ? 3 : 2;
            double bw = anyIdeology ? 152 : 228, bh = anyIdeology ? 24 : 28, rowStep = anyIdeology ? 28 : 34;
            for (int f = 0; f < NF; f++) {
                if (!anyIdeology && FACTIONS[f].party != c.party) continue;
                final int ff = f;
                u.button(g, ix + 20 + (n % cols) * (bw + 4), iy + 10 + (n / cols) * rowStep, bw, bh, FACTIONS[f].name, c.faction == f ? B_TABON : B_NORM, () -> { c.faction = ff; finish(); }, FACTIONS[f].desc + "\n" + FACTIONS[f].impact);
                n++;
            }
            iy += 14 + ((n + cols - 1) / cols) * rowStep;
            Screens.ideologyMap(g, c, ix + 20, iy + 6, 140);
            wrap(g, FACTIONS[c.faction].desc, ix + 176, iy + 16, iw - 196, F_XS, C_TEXT);
            // ratings
            double rx = 960, rw = DW - 48 - rx;
            u.panel(g, rx, y, rw, 700, "Ratings");
            int used = cost();
            text(g, god ? "Unlimited mode" : "Points: " + used + " / " + BUDGET, rx + 20, y + 44, F_BB, god || used <= BUDGET ? C_TEXT : C_BAD);
            u.toggle(g, rx + rw - 60, y + 30, god, () -> god = !god, "Unlimited: no point budget, ratings from 0 to 100.");
            text(g, "Unlimited", rx + rw - 128, y + 44, F_XS, C_DIM);
            for (int i = 0; i < NA; i++) {
                final int k = i;
                double ry = y + 58 + i * 28.5;
                textFit(g, ATTR[i], rx + 20, ry + 16, 150, F_XS, C_TEXT);
                u.hit(rx + 20, ry, 150, 24, null, ATTR[i] + "\n" + ATTR_DESC[i]);
                bar(g, rx + 176, ry + 9, rw - 300, 7, c.a[i] / 100.0, Screens.ratingColor(c.a[i], i == A_SCA));
                text(g, String.valueOf(c.a[i]), rx + rw - 118, ry + 16, F_TB, C_TEXT);
                int step = 5;
                u.button(g, rx + rw - 90, ry + 2, 30, 22, "-", B_NORM, () -> adjust(k, -step));
                u.button(g, rx + rw - 54, ry + 2, 30, 22, "+", B_NORM, () -> adjust(k, step));
            }
            u.button(g, 48, 832, 150, 44, "Back", B_GHOST, () -> { Screens.pickParty = c.party; u.go(SC_PICK); });
            u.button(g, DW - 48 - 620, 832, 150, 44, "Edit platform", B_NORM, () -> { finish(); PlatformEd.open(u, c, SC_CREATE); });
            boolean ok = god || used <= BUDGET;
            u.button(g, DW - 48 - 300, 832, 300, 44, ok ? "Run as " + c.first + " " + c.last : "Over budget", ok ? B_GOLD : B_OFF, ok ? () -> {
                finish();
                if (u.setup.platform != null && "custom".equals(u.setup.platformFor)) c.plat = u.setup.platform.clone();
                u.setup.godMode = god;
                if (c.party == IND) Screens.independentStart(u, c);
                else if (u.setup.mode == MODE_FULL) FieldScreen.open(u, c, false);
                else Screens.startGame(u, c);
            } : null);
        }

        static void applyArchRole() {
            if (arch == 0) c.role = "Governor of " + STATES[Math.max(0, c.home)].name;
            if (arch == 1) c.role = "U.S. Senator from " + STATES[Math.max(0, c.home)].name;
        }

        static void adjust(int k, int d) {
            int lo = god ? 0 : MIN, hi = god ? 100 : MAX;
            int nv = clampi(c.a[k] + d, lo, hi);
            int old = c.a[k];
            c.a[k] = nv;
            if (!god && cost() > BUDGET && (k == A_SCA ? d < 0 : d > 0)) c.a[k] = old;
            finish();
        }

        static double cycle(Battleground2028 u, Graphics2D g, String label, int v, int n, IFn set, double x, double y) {
            text(g, label, x, y + 16, F_XS, C_TEXT);
            u.iconButton(g, x + 100, y + 2, 26, 22, IC_LEFT, () -> { set.on((v + n - 1) % n); Portrait.cache.clear(); }, null);
            for (int i = 0; i < n; i++) {
                g.setColor(i == v ? C_GOLD : new Color(255, 255, 255, 40));
                g.fill(new Ellipse2D.Double(x + 134 + i * 14, y + 10, 7, 7));
            }
            u.iconButton(g, x + 314, y + 2, 26, 22, IC_RIGHT, () -> { set.on((v + 1) % n); Portrait.cache.clear(); }, null);
            return y + 28;
        }
    }

    // =====================================================================
    //  25.  CAMPAIGN HEADQUARTERS  -  the main game screen
    // =====================================================================
    static final class Hq {
        static final double LX = 12, LW = 420, MX = 444, MW = 720, RX = 1176, RW = 412, TOP = 66, BOT = 888;
        static final double MAPY = 102, MAPH = 456;
        static int tab = 0, mapMode = MM_BLANK, sel = -1, rtab = 0, pickSlot = -1, skip = 0;
        static boolean resolving;
        static double resolveT;
        static int newsMark = 0, weekStartNews = 0;
        static String[] digest;
        static double digestT = -100;
        static double prePoll, preCash, preVol, preRaised, preSpent;
        static int preDay = -1;
        static boolean wasPrim;
        static double[] fit = {1, 0, 0};
        static String[] TABS = {"Schedule", "Money", "Field", "Message", "Polls", "Campaign", "News"};

        static void begin(Battleground2028 u) {
            Game g = u.game;
            Camp me = g.me();
            tab = 0; rtab = 0; pickSlot = -1; skip = 0; resolving = false; fastForward = false;
            sel = me != null && me.c.home >= 0 ? me.c.home : -1;
            mapMode = g.phase <= PH_PRIMARY ? MM_CAL : MM_PUBLIC;
            if (g.phase == PH_INVISIBLE) mapMode = MM_BLANK;
            if (me != null && Ballot.running(me) && Ballot.count(me) < NS) mapMode = MM_BALLOT;
            weekStartNews = 0;
            primCacheWeek = -1;
            Polls.cacheWeek = -1;
            if (me != null) {
                Modal m = new Modal();
                m.face = me.c;
                m.title = me.party == IND ? "An independent campaign" : g.set.mode == MODE_FULL ? "Campaign headquarters opens" : "The general election begins";
                Camp gd = g.genCand(DEM), gr = g.genCand(REP);
                m.body = me.party == IND ? (g.set.mode == MODE_FULL
                        ? weekStr(g.week) + ". With the primaries under way, " + me.name() + " launches an independent run for president. " + (gd != null ? gd.last() : "The Democrats") + " and " + (gr != null ? gr.last() : "the Republicans") + " lead their parties' races.\n\n"
                        + "You have no party machine and no ballot lines. Win them state by state: gather petition signatures before each deadline (Field tab, or the Ballot access map). Build name recognition, reach 15% in the polls to make the fall debates, and find a path to 270 - or deny everyone else one and send the race to the House.\n\n"
                        + "Each turn is one week until the final two weeks, which you play one day at a time. Press END WEEK (or Space) when you're ready."
                        : "Labor Day, 2028. " + me.name() + " is on the ballot in every state as an independent, against " + (gd != null ? gd.name() : "the Democrat") + " and " + (gr != null ? gr.name() : "the Republican") + ".\n\n"
                        + "Choose a running mate, reach 15% in the polls to make the debates, and win 270 electoral votes - or deny everyone else a majority and send the race to the House.\n\n"
                        + "Each turn is one week; the final two weeks are played one day at a time.")
                        : g.set.mode == MODE_FULL
                        ? "It is November 9, 2026, the week after the midterms. " + me.name() + " is weighing a run for the " + PARTY[me.party] + " nomination.\n\n"
                        + "Each turn is one week. Fill the candidate's schedule, then spend: TV and digital ads, field offices and organisers, polls. Pick a message every week. Click a state to work it; right-click to send the candidate there.\n\n"
                        + "The final two weeks before Election Day are played one day at a time. Press END WEEK (or Space) when you're ready. F1 opens the briefing."
                        : "Labor Day, 2028. " + me.name() + " is the " + PARTY[me.party] + " nominee against " + g.opp().name() + ". Choose a running mate, negotiate the debates and win 270 electoral votes.\n\n"
                        + "Each turn is one week until the final two weeks, which you play one day at a time. Election Day is November 7.";
                m.buttons = new String[]{"Let's go"};
                m.actions = new Runnable[]{() -> showPending(u)};
                m.w = 720;
                u.modal = m;
            }
        }

        static boolean primaryView(Game g) { return g.phase <= PH_PRIMARY && g.set.mode == MODE_FULL && g.me() != null && g.me().party <= REP; }

        /** The party whose primary the dashboard follows: yours, or the other side's once you've clinched. */
        static int racePartyOf(Game g) { Camp me = g.me(); return me.nominee ? (me.party == DEM ? REP : DEM) : me.party; }

        static Contest contest(Game g, int party, int s) {
            for (Contest c : g.contests) if (c.party == party && c.state == s) return c;
            return null;
        }

        // primary polling by state (public): computed once a week
        static double[][] primCache;
        static int primCacheWeek = -1, primCacheParty = -1;

        static double[] statePrimaryPoll(Game g, int party, int s) {
            if (primCacheWeek != g.week || primCacheParty != party || primCache == null) {
                primCache = new double[NS][];
                Random r = new Random(g.week * 31L + party);
                for (int t = 0; t < NS; t++) {
                    double[] sh = Vote.primaryShares(g, party, t, null);
                    for (int i = 0; i < sh.length; i++) sh[i] = Math.max(0, sh[i] * (1 + r.nextGaussian() * 0.12));
                    primCache[t] = sh;
                }
                primCacheWeek = g.week;
                primCacheParty = party;
            }
            return primCache[s];
        }

        // ---------------------------------------------------------------- frame
        static void update(Battleground2028 u, double dt) {
            Game g = u.game;
            if (fastForward) { spectatorStep(u); return; }
            if (resolving) {
                resolveT += dt;
                if (resolveT > 0.45) { resolving = false; doResolve(u); }
                return;
            }
            if (skip > 0 && u.modal == null && u.popup == null && g.pending.isEmpty() && u.screen == SC_HQ) {
                Camp me = g.me();
                if (me != null) Staff.fill(g, me);
                skip--;
                endWeek(u);
            }
        }

        static void draw(Battleground2028 u, Graphics2D g) {
            Game game = u.game;
            if (game == null) { u.go(SC_HOME); return; }
            u.backdrop(g);
            topBar(u, g, game);
            // left panel
            u.panel(g, LX, TOP, LW, BOT - TOP, null);
            u.tabs(g, LX + 6, TOP + 6, LW - 12, 30, TABS, tab, i -> { tab = i; u.resetScroll("left"); });
            Tabs.draw(u, g, game, tab, LX + 10, TOP + 44, LW - 20, BOT - TOP - 52);
            // map
            mapArea(u, g, game);
            // bottom dashboard
            dashboard(u, g, game);
            // right panel
            u.panel(g, RX, TOP, RW, BOT - TOP, null);
            StatePanel.draw(u, g, game, sel, RX + 12, TOP + 10, RW - 24, BOT - TOP - 20);
            if (resolving) resolvingOverlay(u, g, game);
            if (fastForward) {
                g.setColor(new Color(2, 6, 14, 200));
                g.fillRect(0, 0, DW, DH);
                textC(g, "The race goes on without you", DW / 2, DH / 2 - 20, F_DISPLAY, Color.white);
                textC(g, weekStr(game.week) + "  -  " + PHASE[game.phase], DW / 2, DH / 2 + 20, F_H3, C_GOLD);
                Camp d = game.nominee(DEM), r = game.nominee(REP);
                if (d != null && r != null) textC(g, d.name() + " vs. " + r.name(), DW / 2, DH / 2 + 50, F_B, C_DIM);
            }
        }

        static void topBar(Battleground2028 u, Graphics2D g, Game game) {
            Camp me = game.me();
            g.setPaint(new GradientPaint(0, 0, new Color(10, 18, 34, 250), 0, 58, new Color(6, 11, 22, 250)));
            g.fill(new Rectangle2D.Double(0, 0, DW, 58));
            g.setColor(new Color(124, 196, 255, 40));
            g.draw(new Line2D.Double(0, 58, DW, 58));
            if (me == null) return;
            Portrait.avatar(g, me.c, 36, 29, 21, partyLight(me.party));
            text(g, me.last().toUpperCase() + " 2028", 66, 27, F_H2, partyLight(me.party));
            if (game.daily()) {
                int day = game.week * 7 + game.day;
                int left = ELECTION_WEEK * 7 + 1 - day;
                text(g, DAYS[game.day] + " " + dateStr(day) + "  |  " + (left == 1 ? "Election Day is tomorrow" : left + " days to Election Day"), 66, 45, F_XSB, C_GOLD);
            } else text(g, "Week " + (game.week + 1) + "  |  " + weekStr(game.week) + "  |  " + PHASE[game.phase], 66, 45, F_XS, C_DIM);
            // stat tiles
            double x = 400;
            int w = Math.min(109, Math.max(0, game.week - 1));
            double net = me.raisedHist[w] - me.spentHist[w];
            x = tile(u, g, x, "Cash on hand", money(me.cash), (net >= 0 ? "+" : "") + money(net) + " last week", net >= 0 ? C_GOOD : C_BAD, 150,
                    "Cash on hand: " + money(me.cash) + "\nRaised to date " + money(me.raised) + ", spent " + money(me.spent) + (me.debt > 0 ? "\nDebt: " + money(me.debt) : ""));
            if (me.party == IND) {
                double nat = game.natPubI;
                x = tile(u, g, x, "National poll", fmt1(nat) + "%", nat >= 15 ? "debate stage: yes" : "15% for the debates", nat >= 15 ? C_GOOD : C_DIM, 130, "Your share in the public polling average. Reach 15% to make the debate stage.");
                if (me.onBallot != null && game.phase <= PH_PRIMARY + 1 && Ballot.count(me) < NS) x = tile(u, g, x, "Ballots", Ballot.count(me) + " / 51", Ballot.evOnBallot(me) + " electoral votes", Ballot.evOnBallot(me) >= 270 ? C_GOOD : C_AMBER, 120, "States (and DC) where you have qualified for the ballot. Field tab: ballot access.");
                else { int[] ev = evForecast(game); x = tile(u, g, x, "Electoral votes", String.valueOf(ev[2]), "states you lead", ev[2] >= 270 ? C_GOOD : C_DIM, 130, "States where the public polls have you ahead. 270 wins; if nobody gets there, the House decides."); }
            } else if (primaryView(game)) {
                double[] pp = game.primaryPoll;
                double mine = pp != null ? pp[me.idx] * 100 : 0;
                int rank = 1;
                if (pp != null) for (Camp k : game.camps) if (k.party == me.party && k.active && pp[k.idx] > pp[me.idx]) rank++;
                x = tile(u, g, x, "Primary poll", Math.round(mine) + "%", ordinal(rank) + " of " + game.party(me.party).size(), rank == 1 ? C_GOOD : C_DIM, 150, "Your share in the national " + PARTY[me.party] + " primary polling average.");
                double need = Primary.needed(game, me.party);
                x = tile(u, g, x, "Delegates", fmt0(me.delegates), "of " + fmt0(need) + " needed", C_DIM, 130, "Pledged delegates won so far. A majority of all delegates clinches the nomination.");
            } else {
                double sign = me.party == DEM ? 1 : -1;
                double nat = sign * game.natPubAvg;
                x = tile(u, g, x, "National polls", signed1(nat), Math.abs(nat) < 0.05 ? "tied" : nat >= 0 ? "you lead" : "you trail", nat >= 0 ? C_GOOD : C_BAD, 130, "The public polling average, from your side (positive = you lead).");
                int[] ev = evForecast(game);
                int mineEv = me.party == DEM ? ev[0] : ev[1];
                x = tile(u, g, x, "Electoral votes", String.valueOf(mineEv), "if the public polls are right", mineEv >= 270 ? C_GOOD : C_BAD, 150, "States where the public polls have you ahead. 270 wins.");
            }
            x = meter(u, g, x, "Name ID", me.nameId, C_ACCENT, nameTip(me));
            x = meter(u, g, x, "Enthusiasm", me.enthusiasm, C_GOLD, "How fired-up your base is: drives volunteers, small donors and turnout.");
            x = meter(u, g, x, "Stamina", me.stamina / 100.0, me.stamina < 40 ? C_BAD : C_TEAL, "Tired candidates make mistakes. Rest restores stamina.");
            // buttons
            double bx = DW - 16;
            boolean busy = resolving || u.modal != null;
            bx -= 160;
            u.button(g, bx, 9, 160, 40, resolving ? "Working..." : game.daily() ? "END DAY  >" : "END WEEK  >", busy ? B_OFF : B_GOLD, busy ? null : () -> endWeek(u), game.daily() ? "Resolve the day (Space)." : "Resolve the week (Space).");
            if (game.phase == PH_INVISIBLE && game.week < 50) {
                bx -= 70;
                u.button(g, bx, 9, 64, 40, "+4 wks", busy ? B_OFF : B_NORM, busy ? null : () -> { skip = 3; endWeek(u); }, "Fast-forward four weeks. Your staff fills the schedule; you keep your orders. Stops for any decision.");
            }
            bx -= 60;
            u.button(g, bx, 13, 54, 32, "Help", B_GHOST, () -> Screens.openBriefing(u), "Briefing and rules (F1)");
            bx -= 60;
            u.button(g, bx, 13, 54, 32, "Save", B_GHOST, () -> SaveGame.save(u, SaveGame.SLOT), "Save the game");
            bx -= 60;
            u.button(g, bx, 13, 54, 32, "Menu", B_GHOST, () -> u.modal("Leave the campaign?", "Your game is autosaved every week. Return to the main menu?", new String[]{"Stay", "Main menu"},
                    new Runnable[]{null, () -> { SaveGame.save(u, SaveGame.AUTO); u.go(SC_HOME); }}, null));
        }

        static String nameTip(Camp me) {
            Integer[] idx = new Integer[NS];
            for (int s = 0; s < NS; s++) idx[s] = s;
            Arrays.sort(idx, (a, b) -> Double.compare(me.nameIdS[b], me.nameIdS[a]));
            StringBuilder b = new StringBuilder("National name recognition: " + Math.round(me.nameId * 100) + "% of voters know who you are.");
            b.append("\nHighest: ");
            for (int i = 0; i < 3; i++) b.append(i > 0 ? ", " : "").append(STATES[idx[i]].ab).append(" ").append(Math.round(me.nameIdS[idx[i]] * 100)).append("%");
            b.append("\nLowest: ");
            for (int i = 0; i < 3; i++) b.append(i > 0 ? ", " : "").append(STATES[idx[NS - 1 - i]].ab).append(" ").append(Math.round(me.nameIdS[idx[NS - 1 - i]] * 100)).append("%");
            b.append("\nGrows with ads, digital, visits, doors, podcasts, TV, debates and press coverage. Big podcasts need 15% (mid-size) or 35% (top tier).");
            return b.toString();
        }

        static String ordinal(int n) { return n + (n % 100 >= 11 && n % 100 <= 13 ? "th" : n % 10 == 1 ? "st" : n % 10 == 2 ? "nd" : n % 10 == 3 ? "rd" : "th"); }

        static double tile(Battleground2028 u, Graphics2D g, double x, String lab, String val, String sub, Color subC, double w, String tip) {
            w = Math.max(w, labelWidth(g, lab) + 22);
            label(g, lab, x, 18, C_DIM);
            text(g, val, x, 40, F_NUM, Color.white);
            text(g, sub, x, 53, F_T, subC);
            u.hit(x, 4, w - 10, 52, null, tip);
            g.setColor(new Color(255, 255, 255, 18));
            g.draw(new Line2D.Double(x + w - 12, 12, x + w - 12, 48));
            return x + w;
        }

        static double meter(Battleground2028 u, Graphics2D g, double x, String lab, double v, Color c, String tip) {
            label(g, lab, x, 18, C_DIM);
            text(g, Math.round(v * 100) + "%", x, 40, F_NUM, Color.white);
            bar(g, x, 47, 80, 5, v, c);
            u.hit(x, 4, 90, 52, null, tip);
            return x + 98;
        }

        /** Electoral votes if the public polls are right: {D, R, I}. */
        static int[] evForecast(Game game) {
            double[][] rows = new double[NS][4];
            boolean ind = Polls.indRun(game);
            for (int s = 0; s < NS; s++) {
                double i = ind ? game.pubI[s] : 0, m = game.pubAvg[s];
                rows[s][0] = (100 - i) / 2 + m / 2; rows[s][1] = (100 - i) / 2 - m / 2; rows[s][2] = i; rows[s][3] = i;
            }
            return Vote.electoral3(rows, 3, null);
        }

        // ---------------------------------------------------------------- the map
        static void mapArea(Battleground2028 u, Graphics2D g, Game game) {
            // mode toggles
            boolean prim = primaryView(game);
            Camp me0 = game.me();
            boolean ballot = me0 != null && Ballot.running(me0);
            int[] modes = prim ? new int[]{MM_BLANK, MM_MONEY, MM_INTERNAL, MM_PUBLIC, MM_FIELD, MM_CAL} : ballot ? new int[]{MM_BLANK, MM_MONEY, MM_INTERNAL, MM_PUBLIC, MM_FIELD, MM_BALLOT} : new int[]{MM_BLANK, MM_MONEY, MM_INTERNAL, MM_PUBLIC, MM_FIELD};
            String[] names = new String[modes.length], tips = new String[modes.length];
            int cur = 0;
            for (int i = 0; i < modes.length; i++) {
                names[i] = MAP_MODE[modes[i]];
                tips[i] = MAP_MODE_TIP[modes[i]];
                if (modes[i] == mapMode) cur = i;
            }
            if (!prim && mapMode == MM_CAL) mapMode = MM_PUBLIC;
            if (!ballot && mapMode == MM_BALLOT) mapMode = MM_PUBLIC;
            final int[] fm = modes;
            u.segmented(g, MX, TOP, MW, 30, names, cur, i -> mapMode = fm[i], tips);
            fit = USMap.draw(u, g, game, MX, MAPY, MW, MAPH, mapMode, sel, u.modal == null && u.popup == null);
            USMap.legend(g, game, mapMode, MX + 6, MAPY + MAPH + 2, MW);
            int hs = USMap.hoverState;
            if (hs >= 0 && u.modal == null && u.popup == null) u.tip = stateTip(game, hs);
            drawDigest(u, g);
            if (pickSlot >= 0) {
                g.setColor(new Color(242, 193, 78, 230));
                g.fill(new RoundRectangle2D.Double(MX + MW / 2 - 170, MAPY + 4, 340, 26, 12, 12));
                textC(g, "Click a state for the " + DAYS[pickSlot / 3] + " " + SLOT_NAME[pickSlot % 3].toLowerCase() + " event (Esc cancels)", MX + MW / 2, MAPY + 21, F_XSB, rgb(0x1d1405));
            }
        }

        static final String[] SLOT_NAME = {"Morning", "Afternoon", "Evening"};

        static String stateTip(Game game, int s) {
            StateDef d = STATES[s];
            Camp me = game.me();
            StringBuilder b = new StringBuilder(d.name + "  (" + d.ev + " electoral votes)\n");
            b.append("2024: ").append(margin((d.d24 - d.r24) / d.t24 * 100)).append("   2020: ").append(margin((d.d20 - d.r20) / d.t20 * 100));
            if (primaryView(game)) {
                Contest c = contest(game, me.party, s);
                if (c != null) b.append("\n").append(c.caucus ? "Caucus" : "Primary").append(": ").append(dateStr(c.week * 7 + 1)).append(" - ").append(fmt0(c.delegates)).append(" delegates").append(c.done ? " (done)" : "");
            } else {
                b.append("\nPublic polls: ").append(game.pubN[s] > 0 ? margin(game.pubAvg[s]) : "none yet");
                if (Polls.indRun(game) && game.pubN[s] > 0) {
                    double i3 = game.pubI[s];
                    b.append("   three-way: D ").append(fmt0((100 - i3) / 2 + game.pubAvg[s] / 2)).append(" / R ").append(fmt0((100 - i3) / 2 - game.pubAvg[s] / 2)).append(" / I ").append(fmt0(i3));
                }
            }
            if (me != null && Ballot.running(me)) b.append("\nBallot: ").append(Ballot.status(game, me, s));
            if (me != null) b.append("\nYour name recognition here: ").append(Math.round(me.nameIdS[s] * 100)).append("%");
            if (me != null) {
                InternalPoll p = Polls.latest(game, me.idx, s);
                if (p != null && p.share == null) b.append("\nYour poll (").append(game.week - p.week).append(" wk ago): ").append(margin(p.margin));
                b.append("\nYour spending, last 4 weeks: ").append(money(me.spend4[s]));
                if (me.organizers[s] > 0 || me.offices[s] > 0) b.append("\nOffices ").append(fmt0(me.offices[s])).append(", organisers ").append(fmt0(me.organizers[s]));
                if (me.doorsT[s] > 0) b.append("\nDoors knocked: ").append(compact(me.doorsT[s]));
            }
            return b.toString();
        }

        static void mapPress(Battleground2028 u, double x, double y) {
            if (x < MX || x > MX + MW || y < MAPY || y > MAPY + MAPH) return;
            int s = USMap.stateAt(fit, x, y);
            if (s < 0) return;
            if (pickSlot >= 0) {
                Camp me = u.game.me();
                me.loc[pickSlot] = s;
                pickSlot = -1;
                snd(Sfx.CLICK);
                return;
            }
            sel = s;
            snd(Sfx.CLICK, 0.5, 1.2);
        }

        static void rightClick(Battleground2028 u, double x, double y) {
            if (x < MX || x > MX + MW || y < MAPY || y > MAPY + MAPH) return;
            int s = USMap.stateAt(fit, x, y);
            if (s < 0) return;
            sel = s;
            Tabs.quickVisit(u, s, x, y);
        }

        static void key(Battleground2028 u, KeyEvent e) {
            int k = e.getKeyCode();
            if (k == KeyEvent.VK_ESCAPE) {
                if (pickSlot >= 0) { pickSlot = -1; return; }
                if (sel >= 0) { sel = -1; return; }
            }
            if (k == KeyEvent.VK_SPACE && !resolving) endWeek(u);
            if (k >= KeyEvent.VK_1 && k <= KeyEvent.VK_7) tab = k - KeyEvent.VK_1;
        }

        // ---------------------------------------------------------------- dashboard under the map
        static void dashboard(Battleground2028 u, Graphics2D g, Game game) {
            double y = MAPY + MAPH + 30, h = BOT - y;
            double w1 = 352;
            u.panel(g, MX, y, w1, h, primaryView(game) ? "The " + (racePartyOf(game) == DEM ? "Democratic" : "Republican") + " race" : "The race for 270");
            if (primaryView(game)) raceBars(u, g, game, MX + 14, y + 34, w1 - 28, h - 44);
            else evPanel(u, g, game, MX + 14, y + 34, w1 - 28, h - 44);
            double x2 = MX + w1 + 12, w2 = MW - w1 - 12;
            u.panel(g, x2, y, w2, h, "This week's news");
            newsList(u, g, game, x2 + 12, y + 32, w2 - 24, h - 40, 9);
        }

        static void raceBars(Battleground2028 u, Graphics2D g, Game game, double x, double y, double w, double h) {
            Camp me = game.me();
            List<Camp> cs = new ArrayList<Camp>();
            int rp = racePartyOf(game);
            for (Camp k : game.camps) if (k.party == rp && k.active) cs.add(k);
            final double[] pp = game.primaryPoll;
            if (pp == null) return;
            Collections.sort(cs, (a, b) -> Double.compare(pp[b.idx], pp[a.idx]));
            double max = 0.05;
            for (Camp k : cs) max = Math.max(max, pp[k.idx]);
            int n = Math.min(cs.size(), 8);
            double rowH = Math.min(24, (h - 34) / Math.max(1, n));
            for (int i = 0; i < n; i++) {
                Camp k = cs.get(i);
                double ry = y + i * rowH;
                Portrait.avatar(g, k.c, x + 9, ry + rowH / 2, rowH / 2 - 1.5, k == me ? C_GOLD : null);
                textFit(g, k.last() + (k.announced ? "" : "?"), x + 22, ry + rowH / 2 + 4, 86, k == me ? F_XSB : F_XS, k == me ? Color.white : k.announced ? C_TEXT : C_DIM);
                u.hit(x, ry, w, rowH, null, k.name() + (k.announced ? "" : " has not declared yet - pollsters include potential candidates.") + "\nName recognition: " + Math.round(k.nameId * 100) + "%  |  " + fmt0(k.delegates) + " delegates");
                bar(g, x + 112, ry + rowH / 2 - 4, w - 190, 8, pp[k.idx] / max, USMap.candColour(game, k.idx));
                textR(g, Math.round(pp[k.idx] * 100) + "%", x + w - 46, ry + rowH / 2 + 4, F_XSB, C_TEXT);
                textR(g, fmt0(k.delegates), x + w, ry + rowH / 2 + 4, F_XS, C_DIM);
            }
            textR(g, "delegates", x + w, y - 6, F_T, C_FAINT);
            // next contests
            double ny = y + n * rowH + 12;
            StringBuilder b = new StringBuilder();
            int shown = 0, wk = -1;
            for (Contest c : game.contests) {
                if (c.party != rp || c.done || c.week < game.week) continue;
                if (wk < 0) wk = c.week;
                if (c.week != wk) break;
                b.append(shown++ > 0 ? ", " : "").append(STATES[c.state].ab);
            }
            if (wk >= 0) textFit(g, "Next: " + dateStr(wk * 7 + 1) + " - " + b, x, ny, w, F_XSB, C_GOLD);
        }

        static void evPanel(Battleground2028 u, Graphics2D g, Game game, double x, double y, double w, double h) {
            // electoral vote bar by rating
            boolean three = Polls.indRun(game);
            int nc = three ? 7 : 5;
            int[] cat = new int[nc];
            for (int s = 0; s < NS; s++) {
                double m = game.pubAvg[s];
                int ev = STATES[s].ev;
                if (three) {
                    double i3 = game.pubI[s], d3 = (100 - i3) / 2 + m / 2, r3 = (100 - i3) / 2 - m / 2;
                    double top = Math.max(i3, Math.max(d3, r3)), second = i3 + d3 + r3 - top - Math.min(i3, Math.min(d3, r3));
                    double lead = top - second;
                    int c = lead < 3 ? 3 : top == d3 ? (lead > 8 ? 0 : 1) : top == i3 ? 2 : (lead > 8 ? 6 : 5);
                    if (top == i3 && lead < 3) c = 3;
                    cat[c] += ev;
                } else cat[m > 8 ? 0 : m > 3 ? 1 : m > -3 ? 2 : m > -8 ? 3 : 4] += ev;
            }
            Color[] cc = three ? new Color[]{C_DEM_D, C_DEM, C_IND, rgb(0x8a7fb0), rgb(0x8a7fb0), C_REP, C_REP_D} : new Color[]{C_DEM_D, C_DEM, rgb(0x8a7fb0), C_REP, C_REP_D};
            Camp indC = game.indep();
            String[] cn = three ? new String[]{"Solid D", "Lean D", (indC != null ? indC.last() : "Independent") + " leads", "Toss-up", "Toss-up", "Lean R", "Solid R"} : new String[]{"Solid D", "Lean D", "Toss-up", "Lean R", "Solid R"};
            double bx = x;
            for (int i = 0; i < nc; i++) {
                double bw = w * cat[i] / 538.0;
                g.setColor(cc[i]);
                g.fill(new Rectangle2D.Double(bx, y, bw, 22));
                if (bw > 24) textC(g, String.valueOf(cat[i]), bx + bw / 2, y + 15, F_XSB, Color.white);
                u.hit(bx, y, bw, 22, null, cn[i] + ": " + cat[i] + " electoral votes (public polling)");
                bx += bw;
            }
            g.setColor(Color.white);
            g.fill(new Rectangle2D.Double(x + w * 270 / 538.0 - 1, y - 4, 2, 30));
            textC(g, "270", x + w * 270 / 538.0, y + 38, F_TB, C_TEXT);
            // national trend
            List<double[]> ser = new ArrayList<double[]>();
            int n = Math.max(0, Math.min(109, game.week - 1));
            int from = Math.max(0, game.set.mode == MODE_GENERAL ? GENERAL_START_WEEK : Math.max(0, n - 30));
            if (from > n) from = n;
            int len = Math.max(2, n - from + 1);
            double[] d = new double[len];
            for (int i = 0; i < len; i++) d[i] = game.natHist[Math.min(109, from + i)];
            if (three && game.natIHist != null) {
                double[] dd = new double[len], rr = new double[len], ii = new double[len];
                double hi3 = 40;
                for (int i = 0; i < len; i++) {
                    ii[i] = game.natIHist[Math.min(109, from + i)];
                    dd[i] = (100 - ii[i]) / 2 + d[i] / 2; rr[i] = (100 - ii[i]) / 2 - d[i] / 2;
                    hi3 = Math.max(hi3, Math.max(ii[i], Math.max(dd[i], rr[i])) + 3);
                }
                ser.add(dd); ser.add(rr); ser.add(ii);
                label(g, "National polling average (three-way)", x, y + 60, C_DIM);
                chart(g, x + 26, y + 68, w - 26, h - 76, ser, new Color[]{C_DEM_L, C_REP_L, C_IND}, null, 0, hi3, "%.0f", len);
                return;
            }
            ser.add(d);
            double lo = -8, hi = 8;
            for (double v : d) { lo = Math.min(lo, v - 1); hi = Math.max(hi, v + 1); }
            label(g, "National polling average (D minus R)", x, y + 60, C_DIM);
            chart(g, x + 26, y + 68, w - 26, h - 76, ser, new Color[]{d[len - 1] >= 0 ? C_DEM_L : C_REP_L}, null, lo, hi, "%+.0f", len);
        }

        static void newsList(Battleground2028 u, Graphics2D g, Game game, double x, double y, double w, double h, int max) {
            int from = Math.max(0, game.news.size() - 40);
            double cy = y + 4;
            int shown = 0;
            for (int i = game.news.size() - 1; i >= from && shown < max; i--) {
                News n = game.news.get(i);
                if (n.kind == NK_INFO && n.text.startsWith("Jobs report") && shown > 2) continue;
                Color c = newsColour(n.kind);
                double hh = wrapHeight(g, n.text, w - 60, F_XS);
                if (cy + hh > y + h) break;
                g.setColor(c);
                g.fill(new RoundRectangle2D.Double(x, cy - 9, 3, Math.max(12, hh - 2), 3, 3));
                text(g, n.week == game.week || n.week == game.week - 1 ? "NEW" : "wk " + (n.week + 1), x + 8, cy, F_T, n.week >= game.week - 1 ? C_GOLD : C_FAINT);
                wrap(g, n.text, x + 44, cy, w - 50, F_XS, n.kind == NK_BIG ? Color.white : C_TEXT);
                if (n.state >= 0) u.hit(x, cy - 10, w, hh, () -> sel = n.state, "Click to open " + STATES[n.state].name);
                cy += hh + 5;
                shown++;
            }
        }

        static Color newsColour(int kind) {
            switch (kind) {
                case NK_GOOD: return C_GOOD;
                case NK_BAD: return C_BAD;
                case NK_BIG: return C_GOLD;
                case NK_RESULT: return C_ACCENT;
                case NK_EVENT: return C_AMBER;
                default: return C_FAINT;
            }
        }

        static void resolvingOverlay(Battleground2028 u, Graphics2D g, Game game) {
            double a = Math.min(1, resolveT * 4);
            g.setColor(new Color(4, 8, 16, (int) (120 * a)));
            g.fill(new Rectangle2D.Double(MX, MAPY, MW, MAPH));
            double cx = MX + MW / 2, cy = MAPY + MAPH / 2;
            g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(242, 193, 78, (int) (220 * a)));
            double st = u.tsec * 360 % 360;
            g.draw(new Arc2D.Double(cx - 26, cy - 26, 52, 52, -st, 260, Arc2D.OPEN));
            g.setStroke(new BasicStroke(1f));
            textC(g, game.daily() ? DAYS[game.day] + " on the trail..." : "Week " + (game.week + 1) + " in progress...", cx, cy + 56, F_BB, alpha(Color.white, a));
        }

        // ---------------------------------------------------------------- the turn
        static void endWeek(Battleground2028 u) {
            Game g = u.game;
            Camp me = g.me();
            if (resolving || fastForward || g.phase == PH_OVER) return;
            if (!g.pending.isEmpty()) { skip = 0; showPending(u); return; }
            if (me != null && !me.active && !g.spectator) { lostRace(u); return; }
            // debates this week: the player's own are interactive
            for (Debate d : Debates.thisWeek(g)) {
                if (d.done || d.vp) continue;
                List<Camp> q = Debates.qualifiers(g, d);
                if (me != null && q.contains(me)) { skip = 0; DebateView.start(u, d); return; }
                if (me != null && d.party == me.party && me.active) u.toast("You missed the cut for the " + HOSTS[d.host] + " debate (" + d.thresholdPoll + "% needed).", C_BAD);
            }
            for (Debate d : Debates.thisWeek(g)) {
                if (d.done) continue;
                DebateRun r = Debates.start(g, d);
                r.myIdx = -1;
                while (!r.over) Debates.segment(g, r, -1);
            }
            weekStartNews = g.news.size();
            if (me != null) {
                prePoll = myPoll(g, me);
                preRaised = me.raised;
                preSpent = me.spent;
                preDay = g.day;
                wasPrim = primaryView(g);
                preCash = me.cash;
                preVol = 0;
                for (int s = 0; s < NS; s++) preVol += me.volunteers[s];
            }
            resolving = true;
            resolveT = skip > 0 ? 0.3 : 0;
            snd(Sfx.TURN);
        }

        static void doResolve(Battleground2028 u) {
            Game g = u.game;
            Camp me = g.me();
            int before = g.week;
            int phaseBefore = g.phase;
            List<Integer> myContests = new ArrayList<Integer>();
            for (Contest c : g.contests) if (me != null && c.party == me.party && c.week == g.week && !c.done) myContests.add(g.contests.indexOf(c));
            try {
                Week.resolve(g);
            } catch (RuntimeException ex) {
                ex.printStackTrace();
                u.toast("Simulation error: " + ex, C_BAD);
            }
            primCacheWeek = -1;
            SaveGame.save(u, SaveGame.AUTO);
            // primary night
            if (!myContests.isEmpty()) { skip = 0; primaryNight(u, g, myContests); }
            // phase news
            if (phaseBefore != g.phase && g.phase == PH_CONVENTION && me != null && me.nominee) mapMode = MM_PUBLIC;
            if (g.phase == PH_GENERAL && phaseBefore != PH_GENERAL) mapMode = MM_PUBLIC;
            // debate negotiations once both nominees are set
            if (me != null && me.nominee && g.debateTalks == 0 && g.week >= RNC_WEEK + 1 && g.week < GENERAL_START_WEEK + 2 && g.nominee(DEM) != null && g.nominee(REP) != null) {
                g.debateTalks = 1;
                g.pending.add(new Object[]{EV_DEBATE_DEAL, "Debate negotiations", "", new String[0], -1});
            }
            // money trouble
            if (me != null && me.cash < Week.hqWeek(g, me) * 2 && me.active) u.toast("Cash is running low. Cut spending or raise money.", C_BAD);
            // headline toasts for the player's week
            int nt = 0;
            for (int i = weekStartNews; i < g.news.size() && nt < 3; i++) {
                News n = g.news.get(i);
                if (n.kind == NK_BIG && (n.text.contains(me != null ? me.last() : "~") || n.text.contains("CLINCH") || n.text.contains("WAR") || n.text.contains("CONVENTION") || n.text.contains("ENDORSES"))) { u.toast(n.text, C_GOLD); nt++; }
            }
            if (me != null && (g.week != before || preDay >= 0)) makeDigest(u, g, me, before);
            if (g.phase == PH_OVER) {
                skip = 0;
                if (g.result != null) { Night.start(u); return; }
                if (me != null && !me.active) { lostRace(u); return; }
            }
            if (me != null && !me.active && !g.spectator) { skip = 0; lostRace(u); return; }
            showPending(u);
            if (g.week != before) snd(Sfx.CHIME, 0.4, 1);
        }

        static double myPoll(Game g, Camp me) {
            if (primaryView(g) && g.primaryPoll != null) return g.primaryPoll[me.idx] * 100;
            if (me.party == IND) return g.natPubI;
            return (me.party == DEM ? 1 : -1) * g.natPubAvg;
        }

        static void makeDigest(Battleground2028 u, Game g, Camp me, int week) {
            double poll = myPoll(g, me);
            double vol = 0, doors = 0;
            for (int s = 0; s < NS; s++) { vol += me.volunteers[s]; doors += me.doorsW[s]; }
            String head = null;
            for (int i = weekStartNews; i < g.news.size(); i++) { News n = g.news.get(i); if (n.kind == NK_EVENT || n.kind == NK_BIG) { head = n.text; break; } }
            boolean prim = primaryView(g);
            boolean day = preDay >= 0;
            String pl = prim ? "Primary poll " + Math.round(poll) + "% (" + signed1(poll - prePoll) + ")"
                    : me.party == IND ? "National poll " + fmt1(poll) + "% (" + signed1(poll - prePoll) + ")"
                    : "National " + signed1(poll) + (prim == wasPrim ? " (" + signed1(poll - prePoll) + ")" : "");
            // rallies and shows this turn
            int nr = 0;
            Rally big = null;
            for (Rally r : me.rallies) if (r.week == week && (!day || r.day == preDay)) { nr++; if (big == null || r.crowd > big.crowd) big = r; }
            Appearance top = null;
            int np = 0;
            for (Appearance a : me.media) if (a.week == week && (!day || a.day == preDay) && !a.declined) { np++; if (top == null || a.views > top.views) top = a; }
            String trail = null;
            if (nr > 0 || np > 0) {
                StringBuilder b = new StringBuilder();
                if (nr > 0) b.append(nr).append(nr == 1 ? " rally" : " rallies").append(" (biggest: ").append(thousands(Math.round(big.crowd))).append(" in ").append(Rallies.city(big.state)).append(")");
                if (np > 0) b.append(nr > 0 ? "  |  " : "").append(SHOWS[clampi(top.show, 0, SHOWS.length - 1)].name).append(": ").append(compact(top.views)).append(" views");
                trail = b.toString();
            }
            digest = new String[]{day ? DAYS[preDay] + " in review (" + dateStr(week * 7 + preDay) + ")" : "Week " + (week + 1) + " in review",
                    "Raised " + money(me.raised - preRaised) + ", spent " + money(me.spent - preSpent) + "  |  " + pl,
                    "Volunteers " + compact(vol) + " (" + (vol - preVol >= 0 ? "+" : "") + compact(vol - preVol) + ")  |  doors knocked " + compact(doors) + (day ? " this week" : ""),
                    trail, head};
            digestT = u.tsec;
        }

        static void drawDigest(Battleground2028 u, Graphics2D g) {
            if (digest == null) return;
            double age = u.tsec - digestT;
            if (age > 8 || age < 0) return;
            double a = Math.min(1, Math.min(age * 4, (8 - age) * 1.5));
            int extra = (digest[3] != null ? 1 : 0) + (digest.length > 4 && digest[4] != null ? 1 : 0);
            double w = 560, h = 70 + extra * 20, x = MX + (MW - w) / 2, y = MAPY + 8 - (1 - easeOut(Math.min(1, age * 3))) * 20;
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) clamp(a, 0, 1)));
            RoundRectangle2D rr = new RoundRectangle2D.Double(x, y, w, h, 12, 12);
            g.setColor(new Color(8, 14, 26, 235));
            g.fill(rr);
            g.setColor(new Color(242, 193, 78, 150));
            g.draw(rr);
            text(g, digest[0], x + 16, y + 22, F_SB, C_GOLD);
            text(g, digest[1], x + 16, y + 42, F_XS, C_TEXT);
            text(g, digest[2], x + 16, y + 60, F_XS, C_TEXT);
            double ly = y + 80;
            if (digest[3] != null) { textFit(g, digest[3], x + 16, ly, w - 32, F_XS, C_GOLD); ly += 20; }
            if (digest.length > 4 && digest[4] != null) textFit(g, digest[4], x + 16, ly, w - 32, F_XS, C_DIM);
            g.setComposite(AlphaComposite.SrcOver);
            u.hit(x, y, w, h, () -> digestT = -100, "Click to dismiss");
        }

        static void primaryNight(Battleground2028 u, Game g, List<Integer> idx) {
            Camp me = g.me();
            Modal m = new Modal();
            m.title = idx.size() == 1 ? "Results: " + g.contests.get(idx.get(0)).label : "Primary night: " + idx.size() + " contests";
            m.col = C_ACCENT;
            m.w = 760;
            m.extraH = Math.min(520, 34 + idx.size() * 34);
            m.painter = (gg, x, y, w, h) -> {
                double yy = y + 4;
                label(gg, "State", x, yy + 10, C_DIM);
                label(gg, "Winner", x + 150, yy + 10, C_DIM);
                label(gg, "You", x + 400, yy + 10, C_DIM);
                label(gg, "Your delegates", x + 470, yy + 10, C_DIM);
                yy += 22;
                for (int i : idx) {
                    Contest c = g.contests.get(i);
                    if (yy > y + h - 10) break;
                    double[] r = c.result;
                    if (r == null) continue;
                    int win = USMap.leader(r, g, me.party);
                    text(gg, STATES[c.state].name, x, yy + 18, F_SB, C_TEXT);
                    if (win >= 0) {
                        Camp wk = g.camp(win);
                        Portrait.avatar(gg, wk.c, x + 162, yy + 13, 11, wk == me ? C_GOLD : null);
                        text(gg, wk.last() + " " + Math.round(r[win] * 100) + "%", x + 180, yy + 18, F_SB, wk == me ? C_GOLD : C_TEXT);
                    }
                    text(gg, Math.round(r[me.idx] * 100) + "%", x + 400, yy + 18, F_SB, Color.white);
                    if (c.won != null) {
                        long mine = Math.round(c.won[me.idx]);
                        text(gg, (mine > 0 ? "+" : "") + mine + " of " + fmt0(c.delegates), x + 470, yy + 18, F_SB, mine > 0 ? C_GOOD : C_DIM);
                    } else text(gg, fmt0(c.delegates) + " at stake", x + 470, yy + 18, F_XS, C_DIM);
                    yy += 30;
                }
                text(gg, "Your delegates: " + fmt0(me.delegates) + " of " + fmt0(Primary.needed(g, me.party)) + " needed.", x, y + h - 4, F_BB, C_GOLD);
            };
            m.buttons = new String[]{"Continue"};
            m.actions = new Runnable[]{null};
            boolean won = false;
            for (int i : idx) { Contest c = g.contests.get(i); if (c.result != null && USMap.leader(c.result, g, me.party) == me.idx) won = true; }
            m.body = won ? "Victory speeches tonight. Momentum is yours to lose." : "A tough night. The press is writing your campaign's obituary - prove them wrong.";
            m.face = me.c;
            if (u.modal == null) u.modal = m; else u.modalQueue.add(m);
            snd(won ? Sfx.CHEER : Sfx.ALERT);
        }

        static void lostRace(Battleground2028 u) {
            Game g = u.game;
            Camp me = g.me();
            Camp n = g.nominee(me.party);
            u.modal("The campaign is over", (n != null ? n.name() + " has won the " + PARTY[me.party] + " nomination. " : "") + "Your campaign has ended.\n\nYou can watch the rest of the race unfold - the conventions, the debates and Election Night - or return to the main menu.",
                    new String[]{"Main menu", "Watch Election Night"}, new Runnable[]{() -> u.go(SC_HOME), () -> spectate(u)}, C_AMBER);
        }

        static boolean fastForward;

        static void spectate(Battleground2028 u) {
            Game g = u.game;
            g.spectator = true;
            for (Camp k : g.camps) k.human = false;
            if (g.phase == PH_OVER && g.result == null) g.phase = g.nominee(DEM) != null && g.nominee(REP) != null ? PH_CONVENTION : PH_PRIMARY;
            fastForward = true;
            u.go(SC_HQ);
        }

        /** One week of the race without the player (spectator mode). */
        static void spectatorStep(Battleground2028 u) {
            Game g = u.game;
            g.pending.clear();
            if (g.phase == PH_OVER || g.week > ELECTION_WEEK) {
                fastForward = false;
                if (g.result != null) Night.start(u); else u.go(SC_HOME);
                return;
            }
            for (Debate d : Debates.thisWeek(g)) if (!d.done) { DebateRun r = Debates.start(g, d); r.myIdx = -1; while (!r.over) Debates.segment(g, r, -1); }
            Week.resolve(g);
            g.pending.clear();
        }

        /** Shows the next decision waiting for the player. */
        static void showPending(Battleground2028 u) {
            Game g = u.game;
            if (g.pending.isEmpty() || u.modal != null) return;
            Object[] p = g.pending.get(0);
            int ev = (Integer) p[0];
            Camp me = g.me();
            if (ev == EV_VP) { g.pending.remove(0); Screens.vpSel = -1; u.go(SC_VP); return; }
            if (ev == EV_DEBATE_DEAL) {
                g.pending.remove(0);
                u.modal("Debate negotiations", "Your campaign and " + g.opp().last() + "'s are sitting down to set the debate schedule. How many debates, when, where and on what terms?",
                        new String[]{"Accept the default schedule", "Negotiate"}, new Runnable[]{() -> { g.debateTalks = 2; showPending(u); }, () -> { Screens.negDeal = null; Screens.negNote = null; u.go(SC_NEGOT); }}, C_ACCENT);
                return;
            }
            if (ev == EV_CONCEDE) { g.pending.remove(0); lostRace(u); return; }
            if (ev == EV_ENDORSE) { g.pending.remove(0); if (me != null && me.active) Deals.endorse(u, g, p); else { Endorse.resolve(g, g.camp((Integer) p[4]), null); showPending(u); } return; }
            if (ev == EV_DROPOUT) { g.pending.remove(0); Deals.dropout(u, g, p); return; }
            if (ev == EV_CLOSING) { g.pending.remove(0); if (me != null && me.active) Deals.closing(u, g); else showPending(u); return; }
            String title = (String) p[1], body = (String) p[2];
            String[] opts = (String[]) p[3];
            Modal m = new Modal();
            m.title = title;
            m.body = body;
            m.col = C_AMBER;
            m.stacked = true;
            m.face = me != null ? me.c : null;
            m.w = 760;
            m.buttons = opts;
            m.descs = new String[opts.length];
            m.actions = new Runnable[opts.length];
            for (int i = 0; i < opts.length; i++) {
                final int k = i;
                m.actions[i] = () -> {
                    if (!g.pending.isEmpty()) g.pending.remove(0);
                    Events.decide(g, me, ev, k);
                    u.toast("Decision: " + opts[k], C_ACCENT);
                    showPending(u);
                };
            }
            u.modal = m;
            snd(Sfx.ALERT);
        }
    }

    // =====================================================================
    //  26.  HEADQUARTERS: the left-hand tabs
    // =====================================================================
    static final class Tabs {
        static int pollTab = 0, issueGroup = -1, issueMode = 0;
        static Map<String, double[][]> issueCache = new HashMap<String, double[][]>();
        static int msgTab = 0, pollSize = 2, pollMethod = 1, pollGeo = 0, viewPoll = -1, newsFilter = 0, oppoTarget = -1, fieldSort = 0;

        static void draw(Battleground2028 u, Graphics2D g, Game game, int tab, double x, double y, double w, double h) {
            Camp me = game.me();
            if (me == null) return;
            switch (tab) {
                case 0: schedule(u, g, game, me, x, y, w, h); break;
                case 1: moneyTab(u, g, game, me, x, y, w, h); break;
                case 2: field(u, g, game, me, x, y, w, h); break;
                case 3: message(u, g, game, me, x, y, w, h); break;
                case 4: polls(u, g, game, me, x, y, w, h); break;
                case 5: campaign(u, g, game, me, x, y, w, h); break;
                default: news(u, g, game, me, x, y, w, h); break;
            }
        }

        static Color actColour(int a) {
            switch (a) {
                case ACT_RALLY: case ACT_SPEECH: return rgb(0xf2c14e);
                case ACT_TOWNHALL: case ACT_RETAIL: case ACT_DINNER: return rgb(0x2dd4bf);
                case ACT_FUNDRAISER: case ACT_CALLTIME: return rgb(0x45d483);
                case ACT_PODCAST: case ACT_TV: case ACT_HOSTILE: return rgb(0xb98cff);
                case ACT_UNION: case ACT_CHURCH: case ACT_CAMPUS: case ACT_COMMUNITY: return rgb(0x5aa9ff);
                case ACT_PREP: return rgb(0xff8a5c);
                case ACT_REST: return rgb(0x8a94a8);
                case ACT_ENDORSE: return rgb(0xff6fa8);
                default: return new Color(255, 255, 255, 30);
            }
        }

        static String paramName(int a, int p) {
            if (a == ACT_PODCAST) return SHOWS[clampi(p, 0, SHOWS.length - 1)].name;
            if (a == ACT_CHURCH) return Week.CHURCH_KIND[clampi(p, 0, Week.CHURCH_KIND.length - 1)];
            if (a == ACT_COMMUNITY) return Week.COMMUNITY_KIND[clampi(p, 0, Week.COMMUNITY_KIND.length - 1)];
            return null;
        }

        // ---------------------------------------------------------------- SCHEDULE
        static void schedule(Battleground2028 u, Graphics2D g, Game game, Camp me, double x, double y, double w, double h) {
            boolean daily = game.daily();
            text(g, daily ? "The final stretch: one day at a time" : "The candidate's week", x, y + 14, F_H3, daily ? C_GOLD : C_TEXT);
            textR(g, "Now in " + STATES[me.location].name, x + w, y + 14, F_XS, C_DIM);
            double gy = y + 28, colW = (w - 44) / 3, rowH = 52;
            for (int s3 = 0; s3 < 3; s3++) label(g, Hq.SLOT_NAME[s3], x + 44 + s3 * colW + 4, gy + 10, C_DIM);
            gy += 16;
            int prev = me.location;
            for (int d = 0; d < 7; d++) {
                double ry = gy + d * rowH;
                int[] date = dateOf(game.week * 7 + d);
                boolean past = daily && d < game.day, today = daily && d == game.day;
                boolean eday = game.week * 7 + d == ELECTION_WEEK * 7 + 1;
                if (today) { g.setColor(new Color(242, 193, 78, 26)); g.fill(new RoundRectangle2D.Double(x - 4, ry, w + 8, rowH - 2, 8, 8)); }
                text(g, eday ? "VOTE" : DAYS[d], x, ry + 22, F_SB, past ? C_FAINT : today || eday ? C_GOLD : C_TEXT);
                text(g, today ? "TODAY" : MONTHS[date[1]] + " " + date[2], x, ry + 36, today ? F_TB : F_T, today ? C_GOLD : C_FAINT);
                if (past) {
                    Composite oc = g.getComposite();
                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
                    for (int s3 = 0; s3 < 3; s3++) {
                        int a = me.act[d * 3 + s3];
                        double cx = x + 44 + s3 * colW, cw = colW - 4;
                        u.panel(g, cx, ry + 2, cw, rowH - 6, null);
                        if (a != ACT_NONE) { textFit(g, ACT_SHORT[a], cx + 10, ry + 20, cw - 14, F_XSB, actColour(a)); text(g, "done", cx + 10, ry + 38, F_T, C_FAINT); }
                    }
                    g.setComposite(oc);
                    continue;
                }
                for (int s3 = 0; s3 < 3; s3++) {
                    final int slot = d * 3 + s3;
                    double cx = x + 44 + s3 * colW, cw = colW - 4;
                    int a = me.act[slot];
                    int loc = ACT_LOCAL[a] ? (me.loc[slot] >= 0 ? me.loc[slot] : me.location) : -1;
                    boolean travel = loc >= 0 && loc != prev && s3 != 0 && Math.hypot(STATES[loc].cx - STATES[prev].cx, STATES[loc].cy - STATES[prev].cy) > 220;
                    if (loc >= 0) prev = loc;
                    boolean hov = u.card(g, cx, ry + 2, cw, rowH - 6, Hq.pickSlot == slot, () -> activityMenu(u, game, me, slot), a == ACT_NONE ? "Click to schedule an event" : ACT_NAME[a] + "\n" + ACT_DESC[a]);
                    Color ac = actColour(a);
                    g.setColor(ac);
                    g.fill(new RoundRectangle2D.Double(cx, ry + 2, 4, rowH - 6, 4, 4));
                    if (a == ACT_NONE) { textC(g, "+", cx + cw / 2, ry + 30, F_H2, hov ? C_TEXT : C_FAINT); continue; }
                    textFit(g, ACT_SHORT[a], cx + 10, ry + 20, cw - 14, F_XSB, ac);
                    String pn = paramName(a, me.param[slot]);
                    if (a == ACT_PODCAST) {
                        u.button(g, cx + 8, ry + 26, cw - 14, 18, pn, B_GHOST, () -> podcastMenu(u, me, slot), "Click to choose the show.");
                    } else if (loc >= 0) {
                        double bw = 34;
                        u.button(g, cx + 8, ry + 26, bw, 18, STATES[loc].ab, B_GHOST, () -> { Hq.pickSlot = slot; }, "Where: " + STATES[loc].name + ". Click, then click a state on the map, to move this event.");
                        if (pn != null) textFit(g, pn, cx + 8 + bw + 4, ry + 39, cw - bw - 16, F_T, C_DIM);
                        if (travel) { g.setColor(C_AMBER); g.fill(new Ellipse2D.Double(cx + cw - 10, ry + 6, 6, 6)); u.hit(cx + cw - 14, ry + 2, 14, 14, null, "Long same-day flight: this event is less effective and tiring. Travel overnight (morning) instead."); }
                    } else if (pn != null) textFit(g, pn, cx + 10, ry + 38, cw - 14, F_T, C_DIM);
                    else textFit(g, "remote", cx + 10, ry + 38, cw - 14, F_T, C_FAINT);
                }
            }
            double by = gy + 7 * rowH + 4;
            u.button(g, x, by, 120, 28, "Auto-fill", B_NORM, () -> Staff.fill(game, me), "Your scheduler fills the empty slots.");
            final int from = daily ? game.day * 3 : 0;
            u.button(g, x + 128, by, 100, 28, "Clear", B_NORM, () -> { Arrays.fill(me.act, from, NSLOT, ACT_NONE); Arrays.fill(me.loc, from, NSLOT, -1); }, daily ? "Clear today and the rest of the week." : "Clear the whole week.");
            u.button(g, x + 236, by, w - 236, 28, "Staff runs schedule: " + (game.set.staff[0] ? "ON" : "OFF"), game.set.staff[0] ? B_ON : B_NORM, () -> game.set.staff[0] = !game.set.staff[0],
                    "When on, your scheduler rebuilds the week every turn.");
            // projections
            double fat = 0, cost = 0;
            double fm = 1.3 - me.a(A_STA) / 150.0;
            int s0 = daily ? game.day * 3 : 0, s1 = daily ? s0 + 3 : NSLOT;
            for (int i = s0; i < s1; i++) { fat += ACT_FATIGUE[me.act[i]] * fm * 0.55; cost += Week.eventCost(game, me, me.act[i]); }
            double after = clamp(me.stamina - fat + (38 + 0.12 * me.a(A_STA)) * (daily ? 1.0 / 7 : 1), 5, 100);
            double py = by + 46;
            text(g, daily ? "Stamina after today" : "Stamina after this week", x, py, F_XS, C_TEXT);
            bar(g, x + 160, py - 8, w - 220, 8, after / 100.0, after < 40 ? C_BAD : C_TEAL);
            textR(g, Math.round(after) + "%", x + w, py, F_XSB, after < 40 ? C_BAD : C_TEXT);
            py += 20;
            text(g, daily ? "Event costs today" : "Event costs", x, py, F_XS, C_TEXT);
            textR(g, money(cost), x + w, py, F_XSB, C_TEXT);
            py += 20;
            text(g, "Debate preparation", x, py, F_XS, C_TEXT);
            bar(g, x + 160, py - 8, w - 220, 8, me.prep / 100.0, C_AMBER);
            textR(g, Math.round(me.prep) + "", x + w, py, F_XSB, C_TEXT);
            Debate next = null;
            for (Debate d : game.debates) if (!d.done && d.week >= game.week && (d.party < 0 ? me.nominee : d.party == me.party) && (next == null || d.week < next.week)) next = d;
            if (next != null) { py += 18; text(g, "Next debate: " + dateStr(next.week * 7 + 2) + " (" + HOSTS[next.host] + ")", x, py, F_XS, C_GOLD); }
            // the candidate on the trail
            if (!(me.nominee && game.phase >= PH_CONVENTION) && py + 200 < y + h) {
                double cy0 = py + 20;
                Portrait.draw(g, me.c, x, cy0, 130, 156, u.tsec, 0.08 + 0.1 * Math.max(0, Math.sin(u.tsec * 0.7)), false, true);
                text(g, me.name(), x + 146, cy0 + 22, F_BB, Color.white);
                text(g, me.stamina > 75 ? "Energised and sharp" : me.stamina > 45 ? "Tired but holding up" : "Exhausted - mistakes likely", x + 146, cy0 + 42, F_XS, me.stamina > 45 ? C_DIM : C_BAD);
                label(g, "Momentum", x + 146, cy0 + 70, C_DIM);
                sbar(g, x + 146, cy0 + 78, w - 146, 7, me.momentum, C_BAD, C_GOOD);
                label(g, "Name recognition  " + Math.round(me.nameId * 100) + "%", x + 146, cy0 + 104, C_DIM);
                bar(g, x + 146, cy0 + 112, w - 146, 7, me.nameId, C_ACCENT);
                label(g, "Favourability trend", x + 146, cy0 + 138, C_DIM);
                sbar(g, x + 146, cy0 + 146, w - 146, 7, me.favor * 8, C_BAD, C_GOOD);
            }
            // surrogates
            if (me.nominee && game.phase >= PH_CONVENTION) {
                py += 26;
                label(g, "Surrogates on the trail", x, py, C_DIM);
                py += 8;
                for (int i = 0; i < SURROGATES.length; i++) {
                    if (Integer.parseInt(SURROGATES[i][1]) != me.party) continue;
                    final int k = i;
                    text(g, SURROGATES[i][0], x, py + 18, F_XS, C_TEXT);
                    int st = me.surrogateState[i];
                    u.button(g, x + w - 150, py + 4, 150, 20, st >= 0 ? STATES[st].name : "Stay home", B_GHOST, () -> statePicker(u, game, me, s -> me.surrogateState[k] = s, true), SURROGATES[i][3]);
                    py += 24;
                }
                Cand vp = game.vpOf(me.party);
                if (vp != null) {
                    text(g, vp.name() + " (running mate)", x, py + 18, F_XS, C_TEXT);
                    u.button(g, x + w - 150, py + 4, 150, 20, me.vpSurrogateState >= 0 ? STATES[me.vpSurrogateState].name : "Stay home", B_GHOST, () -> statePicker(u, game, me, s -> me.vpSurrogateState = s, true), null);
                }
            }
        }

        /** Popup of states sorted by value to this campaign. */
        static void statePicker(Battleground2028 u, Game game, Camp me, IFn set, boolean allowNone) {
            double[] v = AI.stateValue(game, me);
            int[] top = AI.topStates(v, 16);
            String[] items = new String[top.length + (allowNone ? 1 : 0)];
            String[] right = new String[items.length];
            for (int i = 0; i < top.length; i++) { items[i] = STATES[top[i]].name; right[i] = game.pubN[top[i]] > 0 && !Hq.primaryView(game) ? margin(game.pubAvg[top[i]]) : ""; }
            if (allowNone) items[top.length] = "Stay home";
            Popup p = u.openPopup(u.mx, u.my - 100, 220, "Send to", items, i -> set.on(i < top.length ? top[i] : -1));
            p.right = right;
        }

        static void activityMenu(Battleground2028 u, Game game, Camp me, int slot) {
            int[] order = {ACT_RALLY, ACT_TOWNHALL, ACT_RETAIL, ACT_DINNER, ACT_SPEECH, ACT_UNION, ACT_CHURCH, ACT_CAMPUS, ACT_COMMUNITY,
                    ACT_FUNDRAISER, ACT_CALLTIME, ACT_PODCAST, ACT_TV, ACT_HOSTILE, ACT_ENDORSE, ACT_PREP, ACT_REST, ACT_NONE};
            String[] items = new String[order.length], right = new String[order.length], tips = new String[order.length];
            Color[] cols = new Color[order.length];
            for (int i = 0; i < order.length; i++) {
                int a = order[i];
                items[i] = ACT_NAME[a];
                right[i] = a == ACT_NONE ? "" : (ACT_COST[a] > 0 ? money(Week.eventCost(game, me, a)) : "free") + (ACT_LOCAL[a] ? "  @ state" : "");
                tips[i] = ACT_DESC[a];
                cols[i] = actColour(a);
            }
            Popup p = u.openPopup(u.mx + 10, u.my - 200, 300, DAYS[slot / 3] + " " + Hq.SLOT_NAME[slot % 3].toLowerCase(), items, i -> {
                int a = order[i];
                me.act[slot] = a;
                if (ACT_LOCAL[a]) {
                    int s = Hq.sel >= 0 ? Hq.sel : me.location;
                    if (a == ACT_FUNDRAISER && !Week.DONOR_HUBS.contains(STATES[s].ab)) s = Hq.sel >= 0 ? Hq.sel : AI.donorHub(game, me, s);
                    me.loc[slot] = s;
                } else me.loc[slot] = -1;
                if (a == ACT_PODCAST) {
                    boolean[] booked = new boolean[SHOWS.length];
                    for (int j = 0; j < NSLOT; j++) if (j != slot && me.act[j] == ACT_PODCAST) booked[clampi(me.param[j], 0, SHOWS.length - 1)] = true;
                    me.param[slot] = Podcasts.aiPick(game, me, booked);
                    podcastMenu(u, me, slot);
                }
                else if (a == ACT_CHURCH || a == ACT_COMMUNITY) paramMenu(u, me, slot, a);
            });
            p.right = right; p.tips = tips; p.cols = cols;
        }

        static void paramMenu(Battleground2028 u, Camp me, int slot, int a) {
            if (a == ACT_PODCAST) { podcastMenu(u, me, slot); return; }
            String[] kinds = a == ACT_CHURCH ? Week.CHURCH_KIND : Week.COMMUNITY_KIND;
            u.openPopup(u.mx + 20, u.my - 60, 260, a == ACT_CHURCH ? "Which congregation?" : "Which community?", kinds, i -> me.param[slot] = i);
        }

        /** The booking menu: real shows by tier; the big ones need name recognition. */
        static void podcastMenu(Battleground2028 u, Camp me, int slot) {
            Game game = u.game;
            int n = SHOWS.length;
            String[] items = new String[n], right = new String[n], tips = new String[n];
            boolean[] en = new boolean[n];
            Color[] cols = new Color[n];
            Color[] tc = {C_DIM, C_TEAL, C_GOLD};
            for (int i = 0; i < n; i++) {
                Show s = SHOWS[i];
                String why = Podcasts.unavailable(game, me, i);
                items[i] = s.name;
                en[i] = why == null;
                right[i] = why != null ? (me.nameId < TIER_NEED[s.tier] ? "needs " + Math.round(TIER_NEED[s.tier] * 100) + "% name ID" : why.startsWith("You were") ? "just had you on" : "won't book you")
                        : i == LOCAL_SHOW ? "~50K" : "~" + compact(s.views * 1e6);
                tips[i] = s.name + " (" + TIER_NAME[s.tier] + ")\n" + s.audience + "." + (s.lean > 0.3 ? " Right-leaning audience." : s.lean < -0.3 ? " Left-leaning audience." : "")
                        + (why != null ? "\n" + why + "." : "\nTypical candidate episode: " + (i == LOCAL_SHOW ? "20-80 thousand" : compact(s.views * 1e6)) + " views. Crossover audiences are harder, but count for more.");
                cols[i] = tc[s.tier];
            }
            Popup p = u.openPopup(u.mx + 20, 20, 400, "Book a show  (your name ID: " + Math.round(me.nameId * 100) + "%)", items, i -> me.param[slot] = i);
            p.right = right; p.tips = tips; p.enabled = en; p.cols = cols;
        }

        /** Right-click on the map: drop the candidate into the next free slot there. */
        static void quickVisit(Battleground2028 u, int s, double x, double y) {
            Game game = u.game;
            Camp me = game.me();
            if (me == null) return;
            String[] items = {"Rally", "Town hall", "Retail stops", "Union hall", "Campus event", "Fundraiser"};
            int[] acts = {ACT_RALLY, ACT_TOWNHALL, ACT_RETAIL, ACT_UNION, ACT_CAMPUS, ACT_FUNDRAISER};
            u.openPopup(x, y, 220, "Send " + me.last() + " to " + STATES[s].name, items, i -> {
                for (int slot = 0; slot < NSLOT; slot++) if (me.act[slot] == ACT_NONE) { me.act[slot] = acts[i]; me.loc[slot] = s; u.toast(ACT_NAME[acts[i]] + " in " + STATES[s].name + " on " + DAYS[slot / 3] + ".", C_GOOD); return; }
                u.toast("The schedule is full - clear a slot first.", C_AMBER);
            });
        }

        // ---------------------------------------------------------------- MONEY
        static void moneyTab(Battleground2028 u, Graphics2D g, Game game, Camp me, double x, double y, double w, double h) {
            double off = u.beginScroll(g, "left", x - 4, y, w + 8, h);
            int h0 = u.hits.size();
            double cy = y - off;
            text(g, money(me.cash), x, cy + 30, F_NUMXL, Color.white);
            text(g, "cash on hand", x, cy + 46, F_XS, C_DIM);
            int lw = Math.min(109, Math.max(0, game.week - 1));
            textR(g, "+" + money(me.raisedHist[lw]) + " raised", x + w, cy + 20, F_SB, C_GOOD);
            textR(g, "-" + money(me.spentHist[lw]) + " spent", x + w, cy + 38, F_SB, C_BAD);
            textR(g, "last week", x + w, cy + 52, F_T, C_FAINT);
            cy += 66;
            // chart
            List<double[]> ser = new ArrayList<double[]>();
            int n = Math.min(26, Math.max(2, game.week));
            double[] r = new double[n], s = new double[n];
            double max = 1e5;
            for (int i = 0; i < n; i++) { int wk = game.week - n + i; if (wk >= 0) { r[i] = me.raisedHist[wk]; s[i] = me.spentHist[wk]; } max = Math.max(max, Math.max(r[i], s[i])); }
            ser.add(r); ser.add(s);
            chart(g, x, cy, w, 90, ser, new Color[]{C_GOOD, C_BAD}, new String[]{"raised", "spent (weekly)"}, 0, max * 1.1, null, n);
            cy += 104;
            label(g, "Where the money comes from", x, cy, C_DIM);
            cy += 10;
            double tot = Math.max(1, me.raised);
            for (int i = 0; i < RS_N; i++) {
                text(g, RS_NAME[i], x, cy + 12, F_XS, C_TEXT);
                bar(g, x + 170, cy + 4, w - 240, 7, me.raisedBy[i] / tot, C_MONEY);
                textR(g, money(me.raisedBy[i]), x + w, cy + 12, F_XSB, C_TEXT);
                cy += 19;
            }
            double cap = (game.phase >= PH_CONVENTION ? 260e6 : 70e6) * (0.4 + me.a(A_BIG) / 100.0);
            text(g, "Big donors tapped", x, cy + 14, F_XS, C_TEXT);
            bar(g, x + 170, cy + 6, w - 240, 7, me.donorsTapped / cap, me.donorsTapped / cap > 0.8 ? C_BAD : C_AMBER);
            u.hit(x, cy, w, 18, null, "Big donors max out. The more you've tapped, the less each fundraiser and bundler meeting raises. A new cycle of limits opens for the general election.");
            textR(g, Math.round(100 * Math.min(1, me.donorsTapped / cap)) + "%", x + w, cy + 14, F_XSB, C_TEXT);
            cy += 34;
            // HQ
            label(g, "Headquarters and staff", x, cy, C_DIM);
            cy += 8;
            String[] tiers = {"Lean", "Standard", "Juggernaut"};
            String[] tips = new String[3];
            for (int i = 0; i < 3; i++) {
                int old = me.hq; me.hq = i; double c = Week.hqWeek(game, me); me.hq = old;
                tips[i] = tiers[i] + ": " + money(c) + " a week. " + (i == 0 ? "Cheap, but ads and events are a little less effective." : i == 2 ? "The best data, ad-makers and advance teams money can buy." : "A professional operation.");
            }
            u.segmented(g, x, cy, w, 28, tiers, me.hq, i -> me.hq = i, tips);
            cy += 34;
            text(g, "Payroll this week: " + money(Week.hqWeek(game, me)), x, cy + 12, F_XS, C_DIM);
            cy += 32;
            // self-funding
            if (me.c.selfMax > 0) {
                label(g, "Self-funding", x, cy, C_DIM);
                final double left = me.c.selfMax - me.selfFunded;
                textR(g, money(me.selfFunded) + " in so far  |  " + money(Math.max(0, left)) + " left", x + w, cy, F_T, C_DIM);
                if (left < 1) { text(g, "You have put in everything you can.", x, cy + 18, F_XS, C_FAINT); cy += 30; }
                else {
                    text(g, me.selfFundW > 0 ? "Every week: " + money(me.selfFundW) + (me.selfFundW > left ? " (last payment: " + money(left) + ")" : "  (" + Math.max(1, (int) Math.ceil(left / me.selfFundW)) + " weeks of money left)") : "No weekly transfer", x, cy + 18, F_XS, C_TEXT);
                    u.slider(g, x, cy + 34, w, me.selfFundW / 50e6, C_MONEY, v -> me.selfFundW = Math.round(v * 100) * 0.5e6,
                            "A fixed transfer from your own fortune that arrives every week until you change it or the money runs out. No donor fatigue, but critics call it buying the race.");
                    u.button(g, x, cy + 48, w, 24, "Write a one-time check...", B_NORM, () -> {
                        double[] amts = {5e6, 10e6, 25e6, 50e6, 100e6, 250e6};
                        List<String> it = new ArrayList<String>();
                        final List<Double> av = new ArrayList<Double>();
                        for (double a0 : amts) if (a0 < left) { it.add(money(a0)); av.add(a0); }
                        it.add("Everything left: " + money(left)); av.add(left);
                        u.openPopup(u.mx, u.my - 120, 260, "A lump sum, today", it.toArray(new String[0]), i -> {
                            double got = Campaign.lumpSum(game, me, av.get(i));
                            u.toast("You write the campaign a check for " + money(got) + ".", C_MONEY);
                        });
                    }, "Put a lump sum in right now. Checks of $50 million or more become a story about buying the race.");
                    cy += 80;
                }
                if (me.selfFunded >= 20e6) {
                    wrap(g, "Progressive voters are starting to hold your personal spending against you (a small penalty that grows with every dollar).", x, cy + 4, w, F_T, C_AMBER);
                    cy += 30;
                }
            }
            // national TV
            label(g, "National TV (weekly gross rating points)", x, cy, C_DIM);
            cy += 8;
            for (int i = 0; i < NATIONAL_TV.length; i++) {
                final int k = i;
                double c = me.nationalGrp[i] * NATIONAL_CPP[i] * (game.week >= 98 ? 1.5 : 1);
                text(g, NATIONAL_TV[i], x, cy + 16, F_XS, C_TEXT);
                text(g, money(c) + "/wk", x, cy + 30, F_T, C_DIM);
                u.stepper(g, x + w - 150, cy + 4, fmt0(me.nationalGrp[i]), () -> me.nationalGrp[k] = Math.max(0, me.nationalGrp[k] - 25), () -> me.nationalGrp[k] = Math.min(400, me.nationalGrp[k] + 25),
                        "About " + money(NATIONAL_CPP[i]) + " per rating point. Reaches every state - including ones you don't need.", 96);
                cy += 40;
            }
            cy += 6;
            // digital
            cy = digitalMix(u, g, game, me, x, cy, w);
            // this week's plan
            double tv = 0, dig = 0, tr = 0, pay = 0;
            for (int m = 0; m < Elect.NM; m++) tv += me.tvGrp[m] * Week.cpp(game, m, true);
            for (int i = 0; i < NATIONAL_TV.length; i++) tv += me.nationalGrp[i] * NATIONAL_CPP[i] * (game.week >= 98 ? 1.5 : 1);
            for (int st = 0; st < NS; st++) { dig += me.digital[st]; tr += me.transferW[st]; pay += me.organizers[st] * Week.ORGANIZER_WEEK * Week.stateCostIndex(st) + me.offices[st] * Week.officeWeek(st); }
            double ev = 0;
            for (int i = 0; i < NSLOT; i++) ev += Week.eventCost(game, me, me.act[i]);
            double total = tv + dig + tr + pay + ev + Week.hqWeek(game, me);
            label(g, "This week's budget", x, cy, C_DIM);
            cy += 6;
            String[][] rows = {{"TV ads", money(tv)}, {"Digital", money(dig)}, {"Field payroll and offices", money(pay)}, {"State party transfers", money(tr)}, {"Candidate events", money(ev)}, {"Headquarters", money(Week.hqWeek(game, me))}};
            for (String[] rr : rows) { text(g, rr[0], x, cy + 16, F_XS, C_TEXT); textR(g, rr[1], x + w, cy + 16, F_XS, C_TEXT); cy += 17; }
            g.setColor(new Color(255, 255, 255, 30));
            g.draw(new Line2D.Double(x, cy + 4, x + w, cy + 4));
            text(g, "Total", x, cy + 20, F_SB, C_TEXT);
            textR(g, money(total), x + w, cy + 20, F_SB, total > me.cash ? C_BAD : C_TEXT);
            if (total > me.cash) text(g, "More than you have: some ads will be pulled.", x, cy + 36, F_T, C_BAD);
            cy += 50;
            u.button(g, x, cy, w / 2 - 4, 26, "Pull all TV", B_NORM, () -> { Arrays.fill(me.tvGrp, 0); Arrays.fill(me.nationalGrp, 0); });
            u.button(g, x + w / 2 + 4, cy, w / 2 - 4, 26, "Pull all digital", B_NORM, () -> Arrays.fill(me.digital, 0));
            cy += 36;
            u.button(g, x, cy, w, 26, "Staff runs ads: " + (game.set.staff[1] ? "ON" : "OFF"), game.set.staff[1] ? B_ON : B_NORM, () -> game.set.staff[1] = !game.set.staff[1], "When on, your ad team places TV and digital each week.");
            cy += 40;
            u.clipHits(h0, y, y + h);
            u.endScroll(g, "left", cy + off - y);
        }

        /** The online ad plan: where the digital budget runs and what each platform says. */
        static double digitalMix(Battleground2028 u, Graphics2D g, Game game, Camp me, double x, double cy, double w) {
            Digital.ensure(me);
            label(g, "Digital: platform mix and message", x, cy, C_DIM);
            double dsum = 0;
            for (int s = 0; s < NS; s++) dsum += me.digital[s];
            textR(g, money(dsum) + "/wk across states", x + w, cy, F_T, C_DIM);
            cy += 8;
            String[] ageTag = {"18-29", "30-64", "65+"};
            for (int p = 0; p < NPLAT; p++) {
                final int k = p;
                double ry = cy + p * 30;
                text(g, PLAT[p], x, ry + 17, F_XS, C_TEXT);
                u.hit(x, ry + 2, 120, 24, null, PLAT_DESC[p] + "\nAbout $" + fmt0(PLAT_CPM[p]) + " per thousand impressions.");
                // age skew: the group this platform over-reaches
                int best = 0;
                for (int a = 1; a < NAGE; a++) if (PLAT_AGE[p][a] > PLAT_AGE[p][best]) best = a;
                text(g, ageTag[best], x + 124, ry + 17, F_T, C_FAINT);
                u.stepper(g, x + 160, ry + 2, Math.round(me.digMix[p] * 100) + "%", () -> Digital.adjust(me, k, -0.05), () -> Digital.adjust(me, k, 0.05), "Share of the digital budget on " + PLAT[p] + ".", 44);
                u.button(g, x + 266, ry + 2, w - 266, 24, DMSG_SHORT[me.digMsg[p]], B_GHOST, () -> {
                    String[] items = DMSG.clone();
                    Popup pp = u.openPopup(u.mx - 120, u.my - 60, 300, PLAT[k] + " ads say...", items, i -> me.digMsg[k] = i);
                    pp.tips = DMSG_DESC;
                }, "What your " + PLAT[p] + " ads say: " + DMSG[me.digMsg[p]] + ".\n" + DMSG_DESC[me.digMsg[p]]);
            }
            cy += NPLAT * 30 + 4;
            double[] ap = Digital.ageProfile(me);
            text(g, "Audience: " + Math.round(ap[0] * 100) + "% under 30, " + Math.round(ap[1] * 100) + "% 30-64, " + Math.round(ap[2] * 100) + "% 65+   |   $" + fmt0(Digital.avgCpm(me)) + " per 1,000", x, cy + 12, F_T, C_DIM);
            cy += 20;
            u.button(g, x, cy, w / 2 - 4, 22, "Let the ad team decide", B_NORM, () -> Digital.aiPlan(game, me), "Your digital director picks a platform mix and messages for this stage of the race.");
            u.button(g, x + w / 2 + 4, cy, w / 2 - 4, 22, "Even mix", B_NORM, () -> { for (int p = 0; p < NPLAT; p++) me.digMix[p] = 1.0 / NPLAT; }, null);
            return cy + 34;
        }

        // ---------------------------------------------------------------- FIELD
        static void field(Battleground2028 u, Graphics2D g, Game game, Camp me, double x, double y, double w, double h) {
            double off = u.beginScroll(g, "left", x - 4, y, w + 8, h);
            int h0 = u.hits.size();
            double cy = y - off;
            if (Ballot.running(me)) cy = ballotAccess(u, g, game, me, x, cy, w);
            double org = 0, off2 = 0, vol = 0, sh = 0, dr = 0, drT = 0, calls = 0, texts = 0, rel = 0, porg = 0;
            for (int s = 0; s < NS; s++) { org += me.organizers[s]; porg += me.partyOrg[s]; off2 += me.offices[s]; vol += me.volunteers[s]; sh += me.shiftsW[s]; dr += me.doorsW[s]; drT += me.doorsT[s]; calls += me.callsW[s]; texts += me.textsW[s]; rel += me.relW[s]; }
            String[][] big = {{"Field offices", fmt0(off2)}, {"Organisers", fmt0(org) + (porg >= 1 ? " + " + fmt0(porg) : "")}, {"Volunteers", compact(vol)}, {"Shifts this week", compact(sh)}};
            for (int i = 0; i < 4; i++) {
                double bx = x + (i % 2) * (w / 2), by = cy + (i / 2) * 50;
                label(g, big[i][0], bx, by + 12, C_DIM);
                text(g, big[i][1], bx, by + 38, F_NUM, Color.white);
            }
            u.hit(x + w / 2, cy, w / 2, 50, null, "Paid organisers, plus organisers your state-party transfers fund.");
            cy += 104;
            label(g, "Voter contact this week", x, cy, C_DIM);
            cy += 6;
            String[][] rows = {{"Doors knocked", compact(dr)}, {"Phone calls", compact(calls)}, {"Texts sent", compact(texts)}, {"Relational contacts (friends & family)", compact(rel)}, {"Doors knocked to date", compact(drT)}};
            for (String[] r : rows) { text(g, r[0], x, cy + 16, F_XS, C_TEXT); textR(g, r[1], x + w, cy + 16, F_XSB, C_TEXT); cy += 18; }
            cy += 10;
            List<double[]> ser = new ArrayList<double[]>();
            int n = Math.max(2, Math.min(109, game.week));
            double[] d = new double[n];
            double max = 1000;
            for (int i = 0; i < n; i++) { d[i] = me.doorsHist[i]; max = Math.max(max, d[i]); }
            ser.add(d);
            chart(g, x, cy, w, 80, ser, new Color[]{rgb(0xb98cff)}, new String[]{"doors per week"}, 0, max * 1.1, null, n);
            cy += 94;
            label(g, "Ground game by state", x, cy, C_DIM);
            cy += 6;
            text(g, "State", x, cy + 14, F_TB, C_FAINT);
            textR(g, "Offices", x + 170, cy + 14, F_TB, C_FAINT);
            textR(g, "Organisers", x + 250, cy + 14, F_TB, C_FAINT);
            textR(g, "Doors/wk", x + w - 8, cy + 14, F_TB, C_FAINT);
            cy += 20;
            double[] v = AI.stateValue(game, me);
            Integer[] idx = new Integer[NS];
            for (int i = 0; i < NS; i++) idx[i] = i;
            final double[] vv = v;
            Arrays.sort(idx, (a, b) -> Double.compare(vv[b] + me.organizers[b] * 1e-3, vv[a] + me.organizers[a] * 1e-3));
            int shown = 0;
            for (int i = 0; i < NS && shown < 18; i++) {
                int s = idx[i];
                if (v[s] <= 0 && me.organizers[s] <= 0 && me.offices[s] <= 0) continue;
                final int st = s;
                boolean hov = u.hover(x, cy, w, 24);
                if (hov || Hq.sel == s) { g.setColor(new Color(255, 255, 255, Hq.sel == s ? 22 : 12)); g.fill(new RoundRectangle2D.Double(x - 4, cy, w + 8, 24, 6, 6)); }
                text(g, STATES[s].name, x, cy + 16, F_XS, C_TEXT);
                textR(g, fmt0(me.offices[s]), x + 170, cy + 16, F_XS, C_TEXT);
                textR(g, fmt0(me.organizers[s]) + (me.organizerOrder[s] != 0 ? (me.organizerOrder[s] > 0 ? " +" : " ") + me.organizerOrder[s] : ""), x + 250, cy + 16, F_XS, me.organizerOrder[s] != 0 ? C_GOLD : C_TEXT);
                textR(g, compact(me.doorsW[s]), x + w - 8, cy + 16, F_XS, C_TEXT);
                u.hit(x, cy, w, 24, () -> { Hq.sel = st; Hq.rtab = 2; }, "Open " + STATES[s].name + "'s field plan");
                cy += 24;
                shown++;
            }
            cy += 12;
            wrap(g, "Organisers recruit and manage volunteers (about 45 active each); offices hold about 8 organisers. Volunteer shifts ramp up toward each state's vote - the last weekend is the busiest of the campaign.", x, cy, w, F_T, C_DIM);
            cy += 50;
            u.button(g, x, cy, w, 26, "Staff runs field: " + (game.set.staff[2] ? "ON" : "OFF"), game.set.staff[2] ? B_ON : B_NORM, () -> game.set.staff[2] = !game.set.staff[2], "When on, your field director hires, fires and opens offices each week.");
            cy += 40;
            u.clipHits(h0, y, y + h);
            u.endScroll(g, "left", cy + off - y);
        }

        /** An independent's fight to get on the ballot, state by state. */
        static double ballotAccess(Battleground2028 u, Graphics2D g, Game game, Camp me, double x, double cy, double w) {
            Ballot.init();
            int n = Ballot.count(me), ev = Ballot.evOnBallot(me);
            label(g, "Ballot access", x, cy + 12, C_GOLD);
            textR(g, n + " of 51 states  |  " + ev + " electoral votes", x + w, cy + 12, F_XSB, ev >= 270 ? C_GOOD : C_AMBER);
            cy += 18;
            bar(g, x, cy, w, 8, ev / 538.0, C_IND);
            g.setColor(Color.white);
            g.fill(new Rectangle2D.Double(x + w * 270 / 538.0 - 1, cy - 3, 2, 14));
            cy += 16;
            if (n >= NS) { text(g, "You're on the ballot everywhere.", x, cy + 12, F_XS, C_GOOD); return cy + 30; }
            cy += wrap(g, "Each state wants valid signatures by its deadline. About " + Math.round(SIG_VALID * 100) + "% of raw signatures survive challenges; paid circulators cost about $" + fmt0(SIG_COST) + " each, volunteers and organisers gather them free. Miss a deadline and you're a write-in.", x, cy + 10, w, F_T, C_DIM) + 6;
            final boolean auto = game.set.staff[2];
            u.button(g, x, cy, w, 24, "Ballot director runs petitions: " + (auto ? "ON" : "OFF"), auto ? B_ON : B_NORM, () -> game.set.staff[2] = !auto, "Shared with the field director (Staff runs field). When on, the petition budget is set every week.");
            cy += 32;
            List<Integer> open = new ArrayList<Integer>();
            for (int s = 0; s < NS; s++) if (!me.onBallot[s]) open.add(s);
            Collections.sort(open, (a, b) -> Integer.compare(Ballot.deadline[a], Ballot.deadline[b]));
            text(g, "State", x, cy + 12, F_TB, C_FAINT);
            text(g, "signatures", x + 110, cy + 12, F_TB, C_FAINT);
            text(g, "due", x + 200, cy + 12, F_TB, C_FAINT);
            textR(g, "paid circulators / wk", x + w, cy + 12, F_TB, C_FAINT);
            cy += 16;
            int shown = 0;
            for (int s : open) {
                if (shown >= 14) break;
                final int st = s;
                boolean gone = game.week > Ballot.deadline[s];
                double pct = Ballot.valid(me, s) / Math.max(1, Ballot.need[s]);
                text(g, STATES[s].name, x, cy + 16, F_XS, gone ? C_FAINT : C_TEXT);
                if (gone) { text(g, "missed - write-in only", x + 110, cy + 16, F_T, C_BAD); cy += 22; shown++; continue; }
                bar(g, x + 110, cy + 9, 60, 6, Math.min(1, pct), pct >= 1 ? C_GOOD : C_AMBER);
                text(g, dateStr(Ballot.deadline[s] * 7 + 1).replaceAll(", 20\\d\\d", ""), x + 200, cy + 16, F_T, Ballot.deadline[s] - game.week <= 3 ? C_BAD : C_DIM);
                u.hit(x, cy, 280, 22, null, STATES[s].name + ": " + thousands(Math.round(Ballot.valid(me, s))) + " of " + thousands(Ballot.need[s]) + " valid signatures needed.");
                if (!auto) u.stepper(g, x + w - 132, cy, money(me.petitionW[s]), () -> me.petitionW[st] = Math.max(0, me.petitionW[st] - 25000), () -> me.petitionW[st] += 25000, "Weekly spending on paid petition circulators in " + STATES[s].name + ".", 78);
                else textR(g, money(me.petitionW[s]), x + w, cy + 16, F_XS, C_DIM);
                cy += 24;
                shown++;
            }
            if (open.size() > shown) { text(g, "+ " + (open.size() - shown) + " more states with later deadlines", x, cy + 12, F_T, C_FAINT); cy += 18; }
            return cy + 16;
        }

        // ---------------------------------------------------------------- MESSAGE
        static void message(Battleground2028 u, Graphics2D g, Game game, Camp me, double x, double y, double w, double h) {
            u.segmented(g, x, y, w, 28, new String[]{"Frame & attack", "Issue focus", "Promises"}, msgTab, i -> { msgTab = i; u.resetScroll("left"); }, null);
            double top = y + 36;
            double off = u.beginScroll(g, "left", x - 4, top, w + 8, h - 36);
            int h0 = u.hits.size();
            double cy = top - off;
            if (msgTab == 0) {
                label(g, "This week's message", x, cy + 12, C_DIM);
                cy += 20;
                for (int f = 0; f < FRAME.length; f++) {
                    final int k = f;
                    double fit = Message.frameFit(game, me, f);
                    boolean sel = me.frame == f;
                    boolean hov = u.card(g, x, cy, w, 30, sel, () -> me.frame = k, FRAME[f] + "\n" + FRAME_DESC[f] + "\nFit for you this week: " + Math.round(fit * 66) + "/100. Sticking with one frame builds a narrative; switching resets it.");
                    text(g, FRAME[f], x + 10, cy + 19, sel ? F_SB : F_XS, sel ? Color.white : C_TEXT);
                    bar(g, x + w - 130, cy + 9, 60, 6, fit / 1.5, fit > 0.95 ? C_GOOD : fit > 0.7 ? C_AMBER : C_BAD);
                    bar(g, x + w - 62, cy + 9, 52, 6, me.frameStr[f] / 1.5, C_GOLD);
                    cy += 33;
                }
                text(g, "fit", x + w - 130, cy + 8, F_T, C_FAINT);
                text(g, "narrative", x + w - 62, cy + 8, F_T, C_FAINT);
                cy += 24;
                Camp t = Message.attackTarget(game, me);
                label(g, "Contrast" + (t != null ? " - against " + t.name() : ""), x, cy + 12, C_DIM);
                if (game.phase <= PH_PRIMARY && !me.nominee) {
                    u.button(g, x + w - 150, cy, 150, 20, "Target: " + (t != null ? t.last() : "-"), B_GHOST, () -> {
                        List<Camp> rivals = game.party(me.party);
                        rivals.remove(me);
                        String[] names = new String[rivals.size()];
                        for (int i = 0; i < names.length; i++) names[i] = rivals.get(i).name();
                        u.openPopup(u.mx, u.my, 220, "Attack whom?", names, i -> me.attackTarget = rivals.get(i).idx);
                    });
                }
                cy += 22;
                for (int a = 0; a < ATTACK.length; a++) {
                    final int k = a;
                    double fit = a == 0 || t == null ? 0 : Message.attackFit(game, t, a);
                    boolean sel = me.attack == a;
                    u.card(g, x, cy, w, 28, sel, () -> me.attack = k, ATTACK[a] + "\n" + ATTACK_DESC[a] + (a > 0 && t != null ? "\nHow well it fits " + t.last() + ": " + Math.round(fit * 83) + "/100." : ""));
                    text(g, ATTACK[a], x + 10, cy + 18, sel ? F_SB : F_XS, sel ? Color.white : C_TEXT);
                    if (a > 0 && t != null) {
                        bar(g, x + w - 130, cy + 10, 60, 6, fit / 1.2, fit > 0.7 ? C_GOOD : fit > 0.4 ? C_AMBER : C_BAD);
                        bar(g, x + w - 62, cy + 10, 52, 6, t.attackStick[a] / 2.0, C_REP_L);
                    }
                    cy += 31;
                }
                text(g, "fit", x + w - 130, cy + 8, F_T, C_FAINT);
                text(g, "has stuck", x + w - 62, cy + 8, F_T, C_FAINT);
                cy += 18;
                text(g, "Backlash risk from going negative", x, cy + 14, F_XS, C_TEXT);
                bar(g, x, cy + 22, w, 7, me.negativity / 0.8, me.negativity > 0.35 ? C_BAD : C_AMBER);
                cy += 40;
                // the closing argument
                if (me.nominee || me.party == IND) {
                    label(g, "Closing argument", x, cy + 12, C_GOLD);
                    cy += 18;
                    if (me.closing >= 0) {
                        text(g, CLOSING[me.closing], x, cy + 14, F_SB, Color.white);
                        text(g, "strength", x, cy + 34, F_T, C_FAINT);
                        bar(g, x + 54, cy + 28, w - 200, 6, me.closingStr / 1.5, C_GOLD);
                        u.button(g, x + w - 136, cy + 2, 136, 24, "Change...", B_NORM, () -> Deals.closing(u, game), "Switching throws away most of what your closing argument has built.");
                        cy += 46;
                    } else {
                        cy += wrap(g, game.week >= DAILY_START - 1 ? "You'll choose your closing argument when the final two weeks begin." : "With two weeks to go, you'll pick a closing argument: the last thing you want voters to hear. It builds every day you stick with it.", x, cy + 12, w, F_T, C_DIM) + 8;
                    }
                }
                u.button(g, x, cy, w, 26, "Staff runs message: " + (game.set.staff[4] ? "ON" : "OFF"), game.set.staff[4] ? B_ON : B_NORM, () -> game.set.staff[4] = !game.set.staff[4]);
                cy += 40;
            } else if (msgTab == 1) {
                cy = issueFocus(u, g, game, me, x, cy, w);
            } else {
                wrap(g, "Promises are permanent. They move your platform, win over some groups and anger others; off-ideology promises cost credibility with your base unless swing voters reward the move.", x, cy + 12, w, F_XS, C_DIM);
                cy += 54;
                for (int p = 0; p < PROMISES.length; p++) {
                    final int k = p;
                    Promise pr = PROMISES[p];
                    boolean made = me.promises.contains(p);
                    double off2 = Campaign.offIdeology(me, p);
                    int clash = Campaign.conflict(me, p);
                    u.card(g, x, cy, w, 40, made, null, Campaign.preview(game, me, p));
                    text(g, pr.name, x + 10, cy + 17, made ? F_SB : F_XS, made ? Color.white : C_TEXT);
                    text(g, ISSUE[pr.issue], x + 10, cy + 32, F_T, C_DIM);
                    if (off2 > 0.6) chip(g, "OFF-IDEOLOGY", x + 120, cy + 21, C_AMBER, false);
                    if (clash >= 0) chip(g, "CONTRADICTS", x + 222, cy + 21, C_BAD, false);
                    if (made) textR(g, "PROMISED", x + w - 10, cy + 25, F_TB, C_GOOD);
                    else u.button(g, x + w - 84, cy + 9, 76, 22, "Promise", B_NORM, () -> u.modal("Make this promise?", pr.name + "\n\n" + Campaign.preview(game, me, k),
                            new String[]{"Not now", "Promise it"}, new Runnable[]{null, () -> Campaign.promise(game, me, k)}, C_GOLD));
                    cy += 44;
                }
            }
            u.clipHits(h0, top, top + h - 36);
            u.endScroll(g, "left", cy + off - top + 10);
        }

        /** One main issue and two secondary ones; credibility (0-100) builds the longer you stay on them. */
        static double issueFocus(Battleground2028 u, Graphics2D g, Game game, Camp me, double x, double cy, double w) {
            Camp op = Issues.rival(game, me);
            // the current focus
            u.panel(g, x, cy, w, 78, null);
            label(g, "Main issue", x + 10, cy + 16, C_GOLD);
            int m = me.focusIssue;
            text(g, ISSUE[m], x + 10, cy + 36, F_SB, Color.white);
            text(g, "credibility " + Issues.credScore(me, m) + "/100  |  " + me.focusWeeks[m] + " wk on it", x + 10, cy + 52, F_T, C_DIM);
            label(g, "Secondary", x + w / 2 + 6, cy + 16, C_TEAL);
            for (int j = 0; j < 2; j++) {
                int i2 = me.focus2[j];
                textFit(g, i2 < 0 ? "(none - pick below)" : ISSUE[i2] + "  " + Issues.credScore(me, i2), x + w / 2 + 6, cy + 36 + j * 17, w / 2 - 14, i2 < 0 ? F_T : F_XS, i2 < 0 ? C_FAINT : C_TEXT);
            }
            text(g, "Credibility compounds: the main issue builds fastest, and every week you stay on an issue it builds faster.", x + 10, cy + 70, F_T, C_FAINT);
            cy += 86;
            Integer[] idx = new Integer[NI];
            for (int i = 0; i < NI; i++) idx[i] = i;
            final double[] mip = Issues.mip(game, -1, -1);
            Arrays.sort(idx, (a, b) -> Double.compare(mip[b], mip[a]));
            text(g, "Issue", x, cy + 10, F_TB, C_FAINT);
            text(g, "top issue for", x + 150, cy + 10, F_TB, C_FAINT);
            text(g, "credibility", x + 232, cy + 10, F_TB, C_FAINT);
            cy += 16;
            for (int i : idx) {
                final int k = i;
                boolean main = m == i, sec = me.focus2[0] == i || me.focus2[1] == i;
                String tip = ISSUE[i] + "\n" + ISSUE_POLES[i][0] + "  <->  " + ISSUE_POLES[i][1] + "\nYour position: " + positionWord(me.plat[i], i)
                        + "\nYour credibility: " + Issues.credScore(me, i) + "/100" + (op != null ? "   " + op.last() + ": " + Issues.credScore(op, i) + "/100" : "")
                        + "\nClick to make it your main issue.";
                u.card(g, x, cy, w, 30, main || sec, () -> Issues.setMain(me, k), tip);
                text(g, ISSUE[i], x + 10, cy + 19, main ? F_SB : F_XS, main ? Color.white : C_TEXT);
                text(g, Math.round(mip[i] * 100) + "%", x + 150, cy + 19, F_XS, C_AMBER);
                int sc = Issues.credScore(me, i);
                bar(g, x + 190 + 42, cy + 12, w - 232 - 96, 6, Math.max(0, sc) / 100.0, sc < 0 ? C_BAD : C_GOOD);
                if (op != null) {
                    double ox = x + 232 + (w - 232 - 96) * clamp(Issues.credScore(op, i) / 100.0, 0, 1);
                    g.setColor(partyLight(op.party));
                    g.fill(new Rectangle2D.Double(ox - 1, cy + 9, 2, 12));
                }
                textR(g, String.valueOf(sc), x + w - 66, cy + 19, F_XSB, C_TEXT);
                if (main) chip(g, "MAIN", x + w - 56, cy + 8, C_GOLD, true);
                else u.button(g, x + w - 56, cy + 5, 50, 20, sec ? "2nd" : "+2nd", sec ? B_ON : B_GHOST, () -> Issues.toggleSecondary(me, k), sec ? "Drop it as a secondary issue." : "Add as one of your two secondary issues.");
                cy += 33;
            }
            if (op != null) text(g, "Tick on each bar: " + op.last() + "'s credibility.", x, cy + 12, F_T, C_FAINT);
            return cy + 24;
        }

        static String positionWord(double v, int i) {
            if (v < -0.6) return "strongly: " + ISSUE_POLES[i][0];
            if (v < -0.2) return "leans: " + ISSUE_POLES[i][0];
            if (v <= 0.2) return "in the middle";
            if (v <= 0.6) return "leans: " + ISSUE_POLES[i][1];
            return "strongly: " + ISSUE_POLES[i][1];
        }

        // ---------------------------------------------------------------- POLLS
        static void polls(Battleground2028 u, Graphics2D g, Game game, Camp me, double x, double y, double w, double h) {
            double off = u.beginScroll(g, "left", x - 4, y, w + 8, h);
            int h0 = u.hits.size();
            double cy = y - off;
            boolean prim = Hq.primaryView(game);
            u.segmented(g, x, cy, w, 28, new String[]{"Horse race", "Issues"}, pollTab, i -> { pollTab = i; u.resetScroll("left"); }, new String[]{"Commission polls and read your results.", "What voters care about, whom they trust on it, and where they stand."});
            cy += 38;
            if (pollTab == 1) {
                cy = issuePolling(u, g, game, me, x, cy, w);
                u.clipHits(h0, y, y + h);
                u.endScroll(g, "left", cy + off - y);
                return;
            }
            label(g, "Commission a poll", x, cy + 12, C_GOLD);
            cy += 22;
            int geo = Hq.sel >= 0 && pollGeo == 1 ? Hq.sel : -1;
            u.segmented(g, x, cy, w, 26, new String[]{"National", Hq.sel >= 0 ? STATES[Hq.sel].name : "Selected state"}, pollGeo, i -> pollGeo = i, null);
            cy += 32;
            String[] sizes = new String[POLL_SIZES.length];
            for (int i = 0; i < sizes.length; i++) sizes[i] = "n=" + POLL_SIZES[i];
            u.segmented(g, x, cy, w, 26, sizes, pollSize, i -> pollSize = i, null);
            cy += 32;
            String[] mt = new String[POLL_METHOD.length];
            for (int i = 0; i < mt.length; i++) mt[i] = POLL_METHOD[i];
            u.segmented(g, x, cy, w, 26, mt, pollMethod, i -> pollMethod = i, new String[]{"Cheap and fast; noisier, misses some groups.", "Text invitations to an online survey. Good value.", "The gold standard: live callers plus text. Expensive, most accurate."});
            cy += 34;
            int n = POLL_SIZES[pollSize];
            double cost = n * POLL_COST_PER[pollMethod] * (geo < 0 ? 1.3 : 1) + 6000;
            double moe = 1.96 * 2 * 100 * Math.sqrt(0.25 / n) / Math.sqrt(POLL_QUALITY[pollMethod]);
            text(g, "Cost " + money(cost) + "   |   margin of error about +/-" + fmt1(moe / 2) + " pts", x, cy + 12, F_XS, C_TEXT);
            boolean noState = pollGeo == 1 && Hq.sel < 0;
            u.button(g, x, cy + 20, w, 30, noState ? "Select a state on the map first" : "Field the poll", noState || me.cash < cost ? B_OFF : B_PRIMARY, noState || me.cash < cost ? null : () -> {
                InternalPoll p = Polls.buy(game, me, geo, n, pollMethod);
                if (p != null) { viewPoll = game.polls.indexOf(p); snd(Sfx.CASH); u.toast("Poll in from " + (geo < 0 ? "the nation" : STATES[geo].name) + ".", C_GOOD); }
            });
            cy += 64;
            // view a poll
            InternalPoll vp = viewPoll >= 0 && viewPoll < game.polls.size() ? game.polls.get(viewPoll) : null;
            if (vp != null && vp.camp == me.idx) cy = pollDetail(u, g, game, me, vp, x, cy, w) + 10;
            // list of my polls
            label(g, "Your polls", x, cy + 12, C_DIM);
            cy += 18;
            int shown = 0;
            for (int i = game.polls.size() - 1; i >= 0 && shown < 14; i--) {
                InternalPoll p = game.polls.get(i);
                if (p.pub || p.camp != me.idx) continue;
                final int k = i;
                String where = p.state < 0 ? "National" : STATES[p.state].name;
                String res = p.share != null ? Math.round(p.share[me.idx] * 100) + "% (you)" : !Double.isNaN(p.ind) && me.party == IND ? Math.round(p.ind) + "% (you)" : margin(p.margin);
                u.card(g, x, cy, w, 26, k == viewPoll, () -> viewPoll = k, null);
                text(g, "wk " + (p.week + 1), x + 8, cy + 17, F_T, C_FAINT);
                text(g, where, x + 50, cy + 17, F_XS, C_TEXT);
                text(g, "n=" + p.n + " " + POLL_METHOD[p.method].split(" ")[0].toLowerCase(), x + 170, cy + 17, F_T, C_DIM);
                textR(g, res, x + w - 8, cy + 17, F_XSB, p.share != null ? C_TEXT : (p.margin >= 0 ? C_DEM_L : C_REP_L));
                cy += 29;
                shown++;
            }
            if (shown == 0) { text(g, "No polls yet.", x, cy + 14, F_XS, C_FAINT); cy += 20; }
            cy += 10;
            if (!prim) {
                label(g, "Public polling averages - battlegrounds", x, cy + 12, C_DIM);
                cy += 18;
                double[] v = AI.stateValue(game, me);
                int[] top = AI.topStates(v, 12);
                for (int s : top) {
                    text(g, STATES[s].name, x, cy + 14, F_XS, C_TEXT);
                    text(g, (int) game.pubN[s] + " polls", x + 160, cy + 14, F_T, C_FAINT);
                    textR(g, game.pubN[s] > 0 ? margin(game.pubAvg[s]) : "-", x + w, cy + 14, F_XSB, game.pubAvg[s] >= 0 ? C_DEM_L : C_REP_L);
                    cy += 19;
                }
            }
            cy += 10;
            label(g, "Your pollster", x, cy + 10, C_DIM);
            cy += 16;
            int lvl = game.set.staff[3] ? game.set.staffPolling + 1 : 0;
            u.segmented(g, x, cy, w, 26, new String[]{"Off", "Light", "Standard", "Heavy"}, lvl, i -> { game.set.staff[3] = i > 0; if (i > 0) game.set.staffPolling = i - 1; },
                    new String[]{"You commission every poll yourself.", "A few key states every month, cheap online samples.", "The main battlegrounds every two weeks, plus a national poll.", "A dozen states every week with live-caller samples. Expensive."});
            cy += 40;
            u.clipHits(h0, y, y + h);
            u.endScroll(g, "left", cy + off - y);
        }

        static double pollDetail(Battleground2028 u, Graphics2D g, Game game, Camp me, InternalPoll p, double x, double cy, double w) {
            u.panel(g, x - 4, cy, w + 8, 10, null);
            double y0 = cy;
            cy += 8;
            String where = p.state < 0 ? "National" : STATES[p.state].name;
            text(g, where + " - week " + (p.week + 1), x + 4, cy + 14, F_SB, Color.white);
            textR(g, "n=" + p.n + ", " + POLL_METHOD[p.method] + ", +/-" + fmt1(p.moe / 2), x + w - 4, cy + 14, F_T, C_DIM);
            cy += 24;
            if (p.share != null) {
                List<Camp> cs = new ArrayList<Camp>();
                for (Camp k : game.camps) if (k.party == me.party && k.idx < p.share.length && p.share[k.idx] > 0.005) cs.add(k);
                final double[] sh = p.share;
                Collections.sort(cs, (a, b) -> Double.compare(sh[b.idx], sh[a.idx]));
                for (int i = 0; i < Math.min(8, cs.size()); i++) {
                    Camp k = cs.get(i);
                    text(g, k.last(), x + 4, cy + 12, k == me ? F_XSB : F_XS, k == me ? C_GOLD : C_TEXT);
                    bar(g, x + 110, cy + 5, w - 170, 7, sh[k.idx] / Math.max(0.01, sh[cs.get(0).idx]), USMap.candColour(game, k.idx));
                    textR(g, Math.round(sh[k.idx] * 100) + "%", x + w - 4, cy + 12, F_XSB, C_TEXT);
                    cy += 17;
                }
            } else {
                if (!Double.isNaN(p.ind)) {
                    double dd = (100 - p.ind) / 2 + p.margin / 2, rr = (100 - p.ind) / 2 - p.margin / 2;
                    Camp ind = game.indep();
                    text(g, "D " + fmt0(dd) + "   R " + fmt0(rr) + "   " + (ind != null ? ind.last() : "Ind") + " " + fmt0(p.ind), x + 4, cy + 12, F_BB, p.ind > Math.max(dd, rr) ? C_IND : C_TEXT);
                } else text(g, "Topline: " + margin(p.margin), x + 4, cy + 12, F_BB, p.margin >= 0 ? C_DEM_L : C_REP_L);
                cy += 22;
                label(g, "Crosstabs (D minus R)", x + 4, cy + 8, C_DIM);
                cy += 14;
                for (int gr = 0; gr < NG; gr++) {
                    double m = p.groupMargin[gr];
                    double gx = x + 4 + (gr % 2) * (w / 2), gy = cy + (gr / 2) * 17;
                    textFit(g, GROUP[gr], gx, gy + 11, w / 2 - 70, F_T, C_TEXT);
                    textR(g, Double.isNaN(m) ? "n/a" : margin(m), gx + w / 2 - 12, gy + 11, F_TB, Double.isNaN(m) ? C_FAINT : m >= 0 ? C_DEM_L : C_REP_L);
                }
                cy += 15 * 17 + 4;
            }
            if (p.mip != null) {
                label(g, "Most important issue", x + 4, cy + 10, C_DIM);
                if (p.trust != null) label(g, "Trust: you minus rival", x + w / 2 + 4, cy + 10, C_DIM);
                cy += 14;
                Integer[] o = new Integer[NI];
                for (int i = 0; i < NI; i++) o[i] = i;
                final double[] mp = p.mip;
                Arrays.sort(o, (a, b) -> Double.compare(mp[b], mp[a]));
                for (int r = 0; r < 5; r++) {
                    int i = o[r];
                    textFit(g, ISSUE[i], x + 4, cy + 11, w / 2 - 50, F_T, C_TEXT);
                    textR(g, Math.round(mp[i] * 100) + "%", x + w / 2 - 12, cy + 11, F_TB, C_AMBER);
                    if (p.trust != null) {
                        textFit(g, ISSUE[i], x + w / 2 + 4, cy + 11, w / 2 - 50, F_T, C_TEXT);
                        textR(g, (p.trust[i] >= 0 ? "+" : "") + Math.round(p.trust[i]), x + w - 4, cy + 11, F_TB, p.trust[i] >= 0 ? C_GOOD : C_BAD);
                    }
                    cy += 15;
                }
                cy += 4;
            }
            g.setColor(new Color(124, 196, 255, 40));
            g.draw(new RoundRectangle2D.Double(x - 4, y0, w + 8, cy - y0 + 4, 10, 10));
            return cy + 4;
        }

        /** The issues view of the polls tab: what matters, whom voters trust, where they stand, and the trends. */
        static double issuePolling(Battleground2028 u, Graphics2D g, Game game, Camp me, double x, double cy, double w) {
            Camp op = Issues.rival(game, me);
            int s = pollGeo == 1 && Hq.sel >= 0 ? Hq.sel : -1;
            u.segmented(g, x, cy, w / 2 - 4, 24, new String[]{"National", Hq.sel >= 0 ? STATES[Hq.sel].ab : "State"}, pollGeo, i -> pollGeo = i, new String[]{"All voters nationwide.", "The state selected on the map."});
            u.button(g, x + w / 2 + 4, cy, w / 2 - 4, 24, issueGroup < 0 ? "All voters" : GROUP[issueGroup], B_GHOST, () -> {
                String[] items = new String[NG + 1];
                items[0] = "All voters";
                for (int i = 0; i < NG; i++) items[i + 1] = GROUP[i];
                u.openPopup(u.mx - 100, 20, 240, "Among...", items, i -> issueGroup = i - 1);
            }, "Look at one group of voters.");
            cy += 32;
            String key = game.week + ":" + game.day + ":" + s + ":" + issueGroup + ":" + me.focusIssue + ":" + (op == null ? -1 : op.idx) + ":" + game.news.size() + ":" + me.promises.size();
            double[][] d = issueCache.get(key);
            if (d == null) {
                if (issueCache.size() > 40) issueCache.clear();
                d = new double[5][NI];
                d[0] = Issues.mip(game, s, issueGroup).clone();
                for (int i = 0; i < NI; i++) {
                    if (op != null) { double[] t = Issues.trust(game, me, op, i, s, issueGroup); d[1][i] = t[0]; d[2][i] = t[1]; }
                    int pp = op != null && op.party == me.party && me.party <= REP ? me.party : -1;
                    d[3][i] = Issues.support(game, me.plat[i], i, s, issueGroup, pp);
                    d[4][i] = op != null ? Issues.support(game, op.plat[i], i, s, issueGroup, pp) : 0;
                }
                issueCache.put(key, d);
            }
            Integer[] o = new Integer[NI];
            for (int i = 0; i < NI; i++) o[i] = i;
            final double[] mp = d[0];
            Arrays.sort(o, (a, b) -> Double.compare(mp[b], mp[a]));
            String scope = (s < 0 ? "nationally" : "in " + STATES[s].name) + (issueGroup >= 0 ? ", " + GROUP[issueGroup].toLowerCase() : "");
            // most important issue
            label(g, "Most important problem facing the country", x, cy + 10, C_GOLD);
            textR(g, scope, x + w, cy + 10, F_T, C_FAINT);
            cy += 16;
            double mx = Math.max(0.01, mp[o[0]]);
            for (int r = 0; r < 8; r++) {
                int i = o[r];
                text(g, ISSUE[i], x, cy + 12, Issues.inFocus(me, i) ? F_XSB : F_XS, Issues.inFocus(me, i) ? C_GOLD : C_TEXT);
                bar(g, x + 170, cy + 5, w - 210, 7, mp[i] / mx, C_AMBER);
                textR(g, Math.round(mp[i] * 100) + "%", x + w, cy + 12, F_XSB, C_TEXT);
                cy += 17;
            }
            cy += 4;
            cy += wrap(g, "Gold: your focus issues. Events, the news and the campaigns' focus push issues up and down.", x, cy + 10, w, F_T, C_FAINT) + 8;
            // trend
            int n = Math.min(26, Math.max(2, game.week));
            List<double[]> ser = new ArrayList<double[]>();
            Color[] cols = {C_AMBER, C_TEAL, C_ACCENT, rgb(0xff6fa8)};
            String[] names = new String[4];
            double hi = 0.05;
            for (int r = 0; r < 4; r++) {
                int i = o[r];
                double[] v = new double[n];
                for (int j = 0; j < n; j++) {
                    int wk = game.week - n + j;
                    double[] h = wk >= 0 && wk < 110 ? game.mipHist[wk] : null;
                    v[j] = h != null ? h[i] : j > 0 ? v[j - 1] : mp[i];
                    hi = Math.max(hi, v[j]);
                }
                ser.add(v);
                names[r] = ISSUE[i].split(" ")[0];
            }
            label(g, "Top issues, last " + n + " weeks (national)", x, cy + 10, C_DIM);
            cy += 16;
            chart(g, x, cy, w, 90, ser, cols, names, 0, hi * 1.15, null, n);
            cy += 104;
            if (op == null) return cy;
            // trust to handle
            label(g, op.party == me.party ? "Primary voters trust more on..." : "Who do you trust more to handle...", x, cy + 10, C_GOLD);
            textR(g, me.last() + " vs " + op.last(), x + w, cy + 10, F_T, C_FAINT);
            cy += 16;
            for (int r = 0; r < 10; r++) {
                int i = o[r];
                double a = d[1][i], b = d[2][i];
                text(g, ISSUE[i], x, cy + 12, Issues.inFocus(me, i) ? F_XSB : F_XS, Issues.inFocus(me, i) ? C_GOLD : C_TEXT);
                double bx = x + 170, bw = w - 210;
                Color mc = op.party == me.party ? C_GOLD : partyColor(me.party);
                g.setColor(mc);
                g.fill(new Rectangle2D.Double(bx, cy + 5, bw * a / 100, 7));
                g.setColor(op.party == me.party ? new Color(150, 160, 185) : partyColor(op.party));
                g.fill(new Rectangle2D.Double(bx + bw * (1 - b / 100), cy + 5, bw * b / 100, 7));
                double m = a - b;
                textR(g, (m >= 0 ? "+" : "") + Math.round(m), x + w, cy + 12, F_XSB, m >= 0 ? C_GOOD : C_BAD);
                u.hit(x, cy, w, 17, null, ISSUE[i] + ": " + me.last() + " " + Math.round(a) + "%, " + op.last() + " " + Math.round(b) + "%, unsure " + Math.round(100 - a - b) + "%.\nTrust follows credibility (stay on an issue to build it), how close your position is to voters', and partisanship.");
                cy += 17;
            }
            cy += 6;
            // trust trend for the focus issues
            int[] fi = {me.focusIssue, me.focus2[0], me.focus2[1]};
            List<double[]> ts = new ArrayList<double[]>();
            List<String> tn = new ArrayList<String>();
            double lo = -5, hi2 = 5;
            // only the weeks measured against the current rival
            int nt = 0;
            for (int wk = game.week - 1; wk >= 0 && nt < 26; wk--) {
                if (wk >= 110 || game.trustHist[wk] == null || game.trustRival == null || game.trustRival[wk] != op.idx) break;
                nt++;
            }
            final int n2 = Math.max(2, nt);
            for (int i : fi) {
                if (i < 0) continue;
                double[] v = new double[n2];
                for (int j = 0; j < n2; j++) {
                    int wk = game.week - n2 + j;
                    double[] h = wk >= 0 && wk < 110 ? game.trustHist[wk] : null;
                    v[j] = h != null ? h[i] : j > 0 ? v[j - 1] : d[1][i] - d[2][i];
                    lo = Math.min(lo, v[j]); hi2 = Math.max(hi2, v[j]);
                }
                ts.add(v);
                tn.add(ISSUE[i].split(" ")[0]);
            }
            label(g, "Trust margin vs " + op.last() + " on your focus issues (national)", x, cy + 10, C_DIM);
            cy += 16;
            if (nt < 2) { text(g, "The trend starts once you've faced " + op.last() + " for a couple of weeks.", x, cy + 12, F_T, C_FAINT); cy -= 60; }
            else chart(g, x, cy, w, 80, ts, cols, tn.toArray(new String[0]), lo - 2, hi2 + 2, null, n2);
            cy += 94;
            // position support
            label(g, op.party == me.party ? "Do primary voters agree with your position?" : "Do voters agree with your position?", x, cy + 10, C_GOLD);
            cy += 16;
            for (int r = 0; r < 10; r++) {
                int i = o[r];
                text(g, ISSUE[i], x, cy + 12, F_XS, C_TEXT);
                double a = d[3][i], b = d[4][i];
                bar(g, x + 170, cy + 5, w - 250, 7, a / 100, a >= 50 ? C_GOOD : C_AMBER);
                double ox = x + 170 + (w - 250) * clamp(b / 100, 0, 1);
                g.setColor(partyLight(op.party));
                g.fill(new Rectangle2D.Double(ox - 1, cy + 2, 2, 13));
                textR(g, Math.round(a) + "%", x + w - 40, cy + 12, F_XSB, C_TEXT);
                textR(g, Math.round(b) + "%", x + w, cy + 12, F_XS, partyLight(op.party));
                u.hit(x, cy, w, 17, null, ISSUE[i] + "\nYou: " + positionWord(me.plat[i], i) + " - " + Math.round(a) + "% agree.\n" + op.last() + ": " + positionWord(op.plat[i], i) + " - " + Math.round(b) + "% agree.\nPromises move your positions (Message tab).");
                cy += 17;
            }
            text(g, "Bar: share who agree with you. Tick and right-hand figure: " + op.last() + ".", x, cy + 12, F_T, C_FAINT);
            return cy + 26;
        }

        // ---------------------------------------------------------------- CAMPAIGN
        static void campaign(Battleground2028 u, Graphics2D g, Game game, Camp me, double x, double y, double w, double h) {
            double off = u.beginScroll(g, "left", x - 4, y, w + 8, h);
            int h0 = u.hits.size();
            double cy = y - off;
            label(g, "Hand it to the staff", x, cy + 12, C_DIM);
            cy += 18;
            for (int i = 0; i < Staff.AREA.length; i++) {
                final int k = i;
                double bx = x + (i % 3) * (w / 3), by = cy + (i / 3) * 28;
                u.toggle(g, bx, by, game.set.staff[i], () -> game.set.staff[k] = !game.set.staff[k], null);
                text(g, Staff.AREA[i], bx + 42, by + 15, F_XS, game.set.staff[i] ? C_TEXT : C_DIM);
            }
            cy += 66;
            // running mate
            if (me.nominee || game.phase >= PH_CONVENTION) {
                label(g, "Running mate", x, cy + 12, C_DIM);
                cy += 18;
                Cand vp = game.vpOf(me.party);
                if (vp != null) {
                    Portrait.draw(g, vp, x, cy, 50, 60, 0, 0, true, true);
                    text(g, vp.name(), x + 60, cy + 22, F_SB, C_TEXT);
                    text(g, vp.role, x + 60, cy + 38, F_T, C_DIM);
                    cy += 70;
                } else {
                    u.button(g, x, cy, w, 30, "Choose a running mate", B_GOLD, () -> { Screens.vpSel = -1; u.go(SC_VP); });
                    cy += 40;
                }
            }
            // debates
            label(g, "Debates", x, cy + 12, C_DIM);
            cy += 18;
            int nd = 0;
            for (Debate d : game.debates) {
                boolean mine = d.party < 0 ? me.nominee || me.party == IND && !d.vp : d.party == me.party;
                if (!mine) continue;
                String what = d.vp ? "VP debate" : d.party < 0 ? (me.party == IND ? "Presidential debate (15% to qualify)" : "Presidential debate") : PARTY[d.party] + " debate (" + d.thresholdPoll + "% to qualify)";
                text(g, dateStr(d.week * 7 + 2), x, cy + 14, F_XS, d.done ? C_FAINT : C_TEXT);
                text(g, what, x + 100, cy + 14, F_XS, d.done ? C_FAINT : C_TEXT);
                textR(g, d.done ? debateRank(d, me) : HOSTS[d.host], x + w, cy + 14, F_T, C_DIM);
                cy += 18;
                nd++;
            }
            if (nd == 0) { text(g, "None scheduled.", x, cy + 14, F_XS, C_FAINT); cy += 18; }
            if (me.nominee && game.phase >= PH_CONVENTION && game.week < 100 && game.opp() != null)
                u.button(g, x, cy + 6, w, 26, "Reopen debate negotiations", B_NORM, () -> { Screens.negDeal = null; Screens.negNote = null; u.go(SC_NEGOT); });
            cy += 44;
            cy = trail(u, g, game, me, x, cy, w);
            // opposition research
            List<Camp> rivals = new ArrayList<Camp>();
            for (Camp k : game.camps) if (k != me && k.active && (k.party == me.party && Hq.primaryView(game) || k.nominee && k.party != me.party && k.party <= REP)) rivals.add(k);
            label(g, "Opposition research", x, cy + 12, C_DIM);
            cy += 18;
            if (!rivals.isEmpty()) {
                if (oppoTarget < 0 || game.camp(oppoTarget) == null || !rivals.contains(game.camp(oppoTarget))) oppoTarget = rivals.get(0).idx;
                Camp t = game.camp(oppoTarget);
                u.button(g, x, cy, 160, 26, t.name(), B_NORM, () -> {
                    String[] names = new String[rivals.size()];
                    for (int i = 0; i < names.length; i++) names[i] = rivals.get(i).name();
                    u.openPopup(u.mx, u.my, 220, "Research whom?", names, i -> oppoTarget = rivals.get(i).idx);
                });
                u.button(g, x + 168, cy, w - 168, 26, "Dig (" + money(Campaign.OPPO_COST) + ")", me.cash >= Campaign.OPPO_COST ? B_NORM : B_OFF, me.cash >= Campaign.OPPO_COST ? () -> Campaign.research(game, me, t) : null,
                        "Researchers comb the record: confirms six of their ratings and raises the chance a scandal surfaces.");
                cy += 32;
                u.button(g, x, cy, w, 26, "Leak a file to the press (" + me.oppoFiles + " ready)", me.oppoFiles > 0 ? B_DANGER : B_OFF, me.oppoFiles > 0 ? () -> Campaign.leak(game, me, t) : null,
                        "Might dominate the news - or be traced back to you.");
                cy += 36;
                cy = rivalCard(u, g, game, me, t, x, cy, w) + 8;
            }
            // endorsements
            label(g, "Endorsements", x, cy + 12, C_DIM);
            cy += 18;
            for (int e = 0; e < ENDORSERS.length; e++) {
                Endorser en = ENDORSERS[e];
                if (en.party >= 0 && en.party != me.party) continue;
                Camp owner = null;
                for (Camp k : game.camps) if (k.endorsements.contains(e)) owner = k;
                text(g, en.name, x, cy + 14, F_XS, owner == me ? C_GOLD : C_TEXT);
                textR(g, owner == null ? "uncommitted" : owner == me ? "YOURS" : owner.last(), x + w, cy + 14, F_T, owner == me ? C_GOOD : C_DIM);
                cy += 18;
            }
            text(g, "Schedule 'Endorsement meeting' events to court them.", x, cy + 14, F_T, C_FAINT);
            cy += 30;
            if (Hq.primaryView(game) && me.active && !me.nominee) {
                u.button(g, x, cy, w, 28, "Suspend the campaign", B_DANGER, () -> u.modal("Suspend the campaign?", "Your run for the " + PARTY[me.party] + " nomination will end. You can endorse a rival on the way out.", new String[]{"Keep fighting", "Suspend"},
                        new Runnable[]{null, () -> Deals.suspend(u, game)}, C_BAD));
                cy += 40;
            }
            u.clipHits(h0, y, y + h);
            u.endScroll(g, "left", cy + off - y);
        }

        static String when(int week, int day) { return day >= 0 ? dateStr(week * 7 + day) : "wk " + (week + 1); }

        /** Rallies, podcasts and the deals you've made. */
        static double trail(Battleground2028 u, Graphics2D g, Game game, Camp me, double x, double cy, double w) {
            // rallies
            label(g, "Rallies", x, cy + 12, C_DIM);
            if (me.rallyCount > 0) textR(g, me.rallyCount + " held  |  " + compact(me.rallyCrowds) + " people  |  avg " + thousands(Math.round(me.rallyCrowds / me.rallyCount)), x + w, cy + 12, F_T, C_DIM);
            cy += 18;
            if (me.rallies.isEmpty()) { text(g, "No rallies yet. Crowds grow with name recognition, enthusiasm and momentum.", x, cy + 12, F_T, C_FAINT); cy += 22; }
            else {
                // crowd sizes, last 16 rallies
                int n = Math.min(16, me.rallies.size());
                double mx = 1;
                for (int i = me.rallies.size() - n; i < me.rallies.size(); i++) mx = Math.max(mx, me.rallies.get(i).crowd);
                double bw = (w - 8) / 16.0;
                for (int i = 0; i < n; i++) {
                    Rally r = me.rallies.get(me.rallies.size() - n + i);
                    double bh = 44 * r.crowd / mx;
                    g.setColor(i == n - 1 ? C_GOLD : alpha(C_GOLD, 0.45f));
                    g.fill(new RoundRectangle2D.Double(x + i * bw + 1, cy + 46 - bh, bw - 3, bh, 3, 3));
                    u.hit(x + i * bw, cy, bw, 48, null, thousands(Math.round(r.crowd)) + " at " + r.venue + " in " + Rallies.city(r.state) + ", " + STATES[r.state].ab + " (" + when(r.week, r.day) + ")");
                }
                cy += 54;
                Rally best = null;
                for (Rally r : me.rallies) if (best == null || r.crowd > best.crowd) best = r;
                for (int i = me.rallies.size() - 1, k = 0; i >= 0 && k < 3; i--, k++) {
                    Rally r = me.rallies.get(i);
                    text(g, when(r.week, r.day), x, cy + 12, F_T, C_FAINT);
                    textFit(g, Rallies.city(r.state) + ", " + STATES[r.state].ab + " - " + r.venue, x + 80, cy + 12, w - 160, F_XS, C_TEXT);
                    textR(g, thousands(Math.round(r.crowd)), x + w, cy + 12, F_XSB, C_TEXT);
                    cy += 16;
                }
                if (best != null) { text(g, "Biggest: " + thousands(Math.round(best.crowd)) + " in " + Rallies.city(best.state) + " (" + when(best.week, best.day) + ")", x, cy + 12, F_T, C_GOLD); cy += 18; }
            }
            cy += 8;
            // podcasts and interviews
            label(g, "Podcasts and long-form interviews", x, cy + 12, C_DIM);
            double tv = 0, td = 0, tr = 0;
            int na = 0;
            for (Appearance a : me.media) if (!a.declined) { tv += a.views; td += a.signups; tr += a.raised; na++; }
            if (na > 0) textR(g, na + " shows  |  " + compact(tv) + " views", x + w, cy + 12, F_T, C_DIM);
            cy += 18;
            if (me.media.isEmpty()) { text(g, "Book shows from the schedule (Podcast / interview). Big shows need name recognition.", x, cy + 12, F_T, C_FAINT); cy += 22; }
            else {
                for (int i = me.media.size() - 1, k = 0; i >= 0 && k < 5; i--, k++) {
                    Appearance a = me.media.get(i);
                    Show s = SHOWS[clampi(a.show, 0, SHOWS.length - 1)];
                    text(g, when(a.week, a.day), x, cy + 12, F_T, C_FAINT);
                    textFit(g, s.name, x + 80, cy + 12, w - 210, F_XS, a.declined ? C_FAINT : C_TEXT);
                    if (a.declined) textR(g, "declined", x + w, cy + 12, F_T, C_BAD);
                    else {
                        String q = a.quality > 0.6 ? "rave" : a.quality > 0.15 ? "good" : a.quality > -0.3 ? "mixed" : "rough";
                        textR(g, compact(a.views) + "  " + q + (a.viral ? "  VIRAL" : ""), x + w, cy + 12, F_XSB, a.quality > 0.15 ? C_GOOD : a.quality > -0.3 ? C_TEXT : C_BAD);
                    }
                    u.hit(x, cy, w, 16, null, a.declined ? s.name + " passed on booking you." : s.name + ": " + thousands(Math.round(a.views)) + " views. +" + thousands(Math.round(a.signups)) + " new small donors, " + money(a.raised) + " raised"
                            + (a.nameGain > 0.0005 ? ", +" + fmt1(a.nameGain * 100) + " pts name ID." : "."));
                    cy += 16;
                }
                text(g, "Total: +" + compact(td) + " small donors and " + money(tr) + " from appearances.", x, cy + 12, F_T, C_DIM);
                cy += 18;
            }
            cy += 8;
            // deals
            if (!me.deals.isEmpty() || me.vpPromised != null) {
                label(g, "Commitments you've made", x, cy + 12, C_DIM);
                cy += 18;
                for (String d : me.deals) { textFit(g, "- " + d, x, cy + 12, w, F_XS, C_TEXT); cy += 16; }
                cy += 8;
            }
            return cy + 6;
        }

        static String debateRank(Debate d, Camp me) {
            if (d.score == null || me.idx >= d.score.length || !d.participants.contains(me.idx)) return "not on stage";
            int rank = 1;
            for (int p : d.participants) if (p != me.idx && p < d.score.length && d.score[p] > d.score[me.idx]) rank++;
            return d.participants.size() == 2 ? (rank == 1 ? "won" : "lost") : Hq.ordinal(rank) + " of " + d.participants.size();
        }

        static double rivalCard(Battleground2028 u, Graphics2D g, Game game, Camp me, Camp t, double x, double cy, double w) {
            Portrait.draw(g, t.c, x, cy, 60, 72, 0, 0, true, true);
            text(g, t.name(), x + 70, cy + 18, F_SB, C_TEXT);
            text(g, FACTIONS[t.c.faction].name, x + 70, cy + 34, F_T, C_DIM);
            text(g, "Cash " + money(t.cash), x + 70, cy + 50, F_T, C_DIM);
            cy += 80;
            for (int i = 0; i < NA; i++) {
                double bx = x + (i % 2) * (w / 2 + 4), by = cy + (i / 2) * 16;
                int v = Campaign.seen(game, me, t, i);
                boolean exact = Campaign.exact(game, me, t, i);
                textFit(g, ATTR_SHORT[i], bx, by + 10, 36, F_T, C_DIM);
                bar(g, bx + 38, by + 4, w / 2 - 74, 6, v / 100.0, exact ? Screens.ratingColor(v, i == A_SCA) : new Color(150, 160, 180));
                textR(g, exact ? String.valueOf(v) : "~" + v, bx + w / 2 - 6, by + 10, F_T, exact ? C_TEXT : C_FAINT);
                u.hit(bx, by, w / 2, 16, null, ATTR[i] + (exact ? ": " + v : ": about " + v + " (estimate - research to confirm)"));
            }
            return cy + 11 * 16 + 4;
        }

        // ---------------------------------------------------------------- NEWS
        static void news(Battleground2028 u, Graphics2D g, Game game, Camp me, double x, double y, double w, double h) {
            u.segmented(g, x, y, w, 26, new String[]{"All", "Headlines", "Results", "Events"}, newsFilter, i -> { newsFilter = i; u.resetScroll("left"); }, null);
            double top = y + 34;
            double off = u.beginScroll(g, "left", x - 4, top, w + 8, h - 34);
            int h0 = u.hits.size();
            double cy = top - off + 12;
            int lastWeek = -1;
            int count = 0;
            for (int i = game.news.size() - 1; i >= 0 && count < 300; i--) {
                News n = game.news.get(i);
                if (newsFilter == 1 && n.kind != NK_BIG) continue;
                if (newsFilter == 2 && n.kind != NK_RESULT) continue;
                if (newsFilter == 3 && n.kind != NK_EVENT && n.kind != NK_BAD && n.kind != NK_GOOD) continue;
                if (n.week != lastWeek) {
                    lastWeek = n.week;
                    label(g, "Week " + (n.week + 1) + "  -  " + weekStr(n.week), x, cy + 4, C_GOLD);
                    cy += 18;
                }
                double hh = wrapHeight(g, n.text, w - 14, F_XS);
                if (cy > top - 40 && cy < top + h) {
                    g.setColor(Hq.newsColour(n.kind));
                    g.fill(new RoundRectangle2D.Double(x, cy - 9, 3, Math.max(12, hh - 2), 3, 3));
                    wrap(g, n.text, x + 10, cy, w - 14, F_XS, n.kind == NK_BIG ? Color.white : C_TEXT);
                }
                cy += hh + 6;
                count++;
            }
            u.clipHits(h0, top, top + h - 34);
            u.endScroll(g, "left", cy + off - top);
        }
    }

    // =====================================================================
    //  27.  HEADQUARTERS: the state panel (right) and the national picture
    // =====================================================================
    static final class StatePanel {
        static double[] groupShareCache;
        static int groupShareState = -2;
        static double[] issueCache;

        static void draw(Battleground2028 u, Graphics2D g, Game game, int s, double x, double y, double w, double h) {
            Camp me = game.me();
            if (me == null) return;
            if (s < 0) { national(u, g, game, me, x, y, w, h); return; }
            StateDef d = STATES[s];
            text(g, d.name, x, y + 26, F_H1, Color.white);
            chip(g, d.ev + " EV", x + strW(g, d.name, F_H1) + 10, y + 12, C_GOLD, false);
            u.iconButton(g, x + w - 26, y + 6, 24, 24, IC_CLOSE, () -> Hq.sel = -1, "Back to the national picture (Esc)");
            double m24 = (d.d24 - d.r24) / d.t24 * 100, m20 = (d.d20 - d.r20) / d.t20 * 100;
            text(g, "2024 " + margin(m24) + "   2020 " + margin(m20) + "   2022 statewide " + margin(d.m22), x, y + 46, F_XS, C_DIM);
            u.segmented(g, x, y + 56, w, 26, new String[]{"Overview", "Ads", "Field", "Voters"}, Hq.rtab, i -> { Hq.rtab = i; u.resetScroll("right"); }, null);
            double top = y + 90;
            double off = u.beginScroll(g, "right", x - 4, top, w + 8, h - 90);
            int h0 = u.hits.size();
            double cy = top - off;
            switch (Hq.rtab) {
                case 0: cy = overview(u, g, game, me, s, x, cy, w); break;
                case 1: cy = ads(u, g, game, me, s, x, cy, w); break;
                case 2: cy = field(u, g, game, me, s, x, cy, w); break;
                default: cy = voters(u, g, game, me, s, x, cy, w); break;
            }
            u.clipHits(h0, top, top + h - 90);
            u.endScroll(g, "right", cy + off - top + 10);
        }

        static double tvInState(Camp k, int s) {
            double t = 0;
            if (k == null || k.tvGrp == null) return 0;
            for (int m = 0; m < Elect.NM; m++) if (k.tvGrp[m] > 0) t += k.tvGrp[m] * Elect.dmaShare[m][s];
            for (int n = 0; n < NATIONAL_TV.length; n++) t += k.nationalGrp[n] * (n == 2 ? STATES[s].hisp / 30.0 : 1) * (n == 1 ? 0.6 : 0.9);
            return t;
        }

        static double overview(Battleground2028 u, Graphics2D g, Game game, Camp me, int s, double x, double cy, double w) {
            boolean prim = Hq.primaryView(game);
            if (prim) {
                Contest c = Hq.contest(game, me.party, s);
                if (c != null) {
                    label(g, (c.caucus ? "Caucus" : "Primary") + (c.done ? " - result" : " - " + dateStr(c.week * 7 + 1)), x, cy + 12, C_GOLD);
                    text(g, fmt0(c.delegates) + " delegates" + (c.wta ? ", winner-take-all" : me.party == DEM ? ", proportional (15% threshold)" : ", proportional"), x, cy + 30, F_XS, C_TEXT);
                    if (!c.done) text(g, (c.week - game.week) + " weeks away", x + w - 90, cy + 30, F_XS, C_DIM);
                    cy += 40;
                    double[] sh = c.done && c.result != null ? c.result : Hq.statePrimaryPoll(game, me.party, s);
                    if (sh != null) {
                        label(g, c.done ? "Final result" : "Public polling (estimate)", x, cy + 10, C_DIM);
                        cy += 16;
                        List<Camp> cs = new ArrayList<Camp>();
                        for (Camp k : game.camps) if (k.party == me.party && k.idx < sh.length && sh[k.idx] > 0.004) cs.add(k);
                        final double[] ss = sh;
                        Collections.sort(cs, (a, b) -> Double.compare(ss[b.idx], ss[a.idx]));
                        double max = cs.isEmpty() ? 1 : sh[cs.get(0).idx];
                        for (int i = 0; i < Math.min(6, cs.size()); i++) {
                            Camp k = cs.get(i);
                            text(g, k.last(), x, cy + 13, k == me ? F_XSB : F_XS, k == me ? C_GOLD : C_TEXT);
                            bar(g, x + 100, cy + 6, w - 150, 7, sh[k.idx] / Math.max(0.01, max), USMap.candColour(game, k.idx));
                            textR(g, Math.round(sh[k.idx] * 100) + "%", x + w, cy + 13, F_XSB, C_TEXT);
                            cy += 18;
                        }
                    }
                    cy += 6;
                }
            } else {
                label(g, "Polling", x, cy + 12, C_GOLD);
                cy += 18;
                text(g, "Public average", x, cy + 14, F_XS, C_TEXT);
                textR(g, game.pubN[s] > 0 ? margin(game.pubAvg[s]) + "  (" + (int) game.pubN[s] + " polls)" : "no public polls", x + w, cy + 14, F_XSB, game.pubAvg[s] >= 0 ? C_DEM_L : C_REP_L);
                cy += 20;
                if (Polls.indRun(game) && game.pubN[s] > 0) {
                    double i = game.pubI[s], dd = (100 - i) / 2 + game.pubAvg[s] / 2, rr = (100 - i) / 2 - game.pubAvg[s] / 2;
                    Camp ind = game.indep();
                    text(g, "Three-way", x, cy + 14, F_XS, C_TEXT);
                    textR(g, "D " + fmt0(dd) + "   R " + fmt0(rr) + "   " + (ind != null ? ind.last() : "Ind") + " " + fmt0(i), x + w, cy + 14, F_XSB, i > Math.max(dd, rr) ? C_IND : C_TEXT);
                    cy += 20;
                }
                InternalPoll p = Polls.latest(game, me.idx, s);
                text(g, "Your latest poll", x, cy + 14, F_XS, C_TEXT);
                textR(g, p == null ? "none" : margin(p.margin) + "  (+/-" + fmt1(p.moe / 2) + ", " + (game.week - p.week) + " wk ago)", x + w, cy + 14, F_XSB, p == null ? C_FAINT : p.margin >= 0 ? C_DEM_L : C_REP_L);
                cy += 26;
            }
            InternalPoll ip = Polls.latest(game, me.idx, s);
            int n = 600, meth = 1;
            double cost = n * POLL_COST_PER[meth] + 6000;
            u.button(g, x, cy, w, 26, "Poll " + STATES[s].name + " (600, text-to-web, " + money(cost) + ")", me.cash >= cost ? B_PRIMARY : B_OFF, me.cash >= cost ? () -> {
                InternalPoll np = Polls.buy(game, me, s, n, meth);
                if (np != null) { Tabs.viewPoll = game.polls.indexOf(np); u.toast("New poll of " + STATES[s].name + " is in (Polls tab).", C_GOOD); snd(Sfx.CASH); }
            } : null, "Bigger or better polls: Polls tab.");
            cy += 36;
            label(g, "Your operation here", x, cy + 12, C_GOLD);
            cy += 16;
            text(g, "Name recognition here", x, cy + 15, F_XS, C_TEXT);
            bar(g, x + 150, cy + 8, w - 200, 6, me.nameIdS[s], C_ACCENT);
            textR(g, Math.round(me.nameIdS[s] * 100) + "%", x + w, cy + 15, F_XSB, C_TEXT);
            cy += 18;
            if (me.party == IND && me.onBallot != null) {
                String st = Ballot.status(game, me, s);
                text(g, "Ballot access", x, cy + 15, F_XS, C_TEXT);
                textR(g, st, x + w, cy + 15, F_XSB, me.onBallot[s] ? C_GOOD : C_AMBER);
                cy += 18;
            }
            int rc = 0; double best = 0;
            for (Rally r : me.rallies) if (r.state == s) { rc++; best = Math.max(best, r.crowd); }
            if (rc > 0) {
                text(g, "Rallies here", x, cy + 15, F_XS, C_TEXT);
                textR(g, rc + " (biggest: " + thousands(Math.round(best)) + ")", x + w, cy + 15, F_XSB, C_TEXT);
                cy += 18;
            }
            double tv = tvInState(me, s);
            String[][] rows = {
                    {"Spending (last 4 weeks)", money(me.spend4[s])}, {"Spending to date", money(me.spendState[s])},
                    {"TV this week", fmt0(tv) + " GRPs"}, {"Digital this week", money(me.digital[s])},
                    {"Field offices / organisers", fmt0(me.offices[s]) + " / " + fmt0(me.organizers[s])}, {"Volunteers", compact(me.volunteers[s])},
                    {"Doors this week / to date", compact(me.doorsW[s]) + " / " + compact(me.doorsT[s])},
                    {"Candidate attention", me.visit[s] > 3 ? "heavy" : me.visit[s] > 1 ? "recent visits" : me.visit[s] > 0.2 ? "light" : "none lately"}};
            for (String[] r : rows) { text(g, r[0], x, cy + 15, F_XS, C_TEXT); textR(g, r[1], x + w, cy + 15, F_XSB, C_TEXT); cy += 18; }
            cy += 8;
            // what the other side is doing (ad trackers and press reports)
            List<Camp> rivals = new ArrayList<Camp>();
            if (prim) { for (Camp k : game.party(me.party)) if (k != me) rivals.add(k); }
            else if (game.opp() != null) rivals.add(game.opp());
            if (!rivals.isEmpty()) {
                label(g, prim ? "Rivals here (ad trackers, press)" : "Opponent here (ad trackers, press)", x, cy + 12, C_DIM);
                cy += 16;
                Collections.sort(rivals, (a, b) -> Double.compare(tvInState(b, s) + b.visit[s] * 50, tvInState(a, s) + a.visit[s] * 50));
                for (int i = 0; i < Math.min(4, rivals.size()); i++) {
                    Camp k = rivals.get(i);
                    double t = tvInState(k, s);
                    text(g, k.last(), x, cy + 14, F_XS, C_TEXT);
                    textR(g, fmt0(t) + " GRPs" + (k.visit[s] > 1 ? ", visited" : "") + (k.offices[s] > 0 ? ", " + fmt0(k.offices[s]) + " offices" : ""), x + w, cy + 14, F_XS, C_DIM);
                    cy += 17;
                }
                cy += 8;
            }
            // general-election extras
            if (!prim) {
                for (SenateRace r : game.senate) if (r.state == s) {
                    label(g, "Senate race", x, cy + 12, C_DIM);
                    cy += 16;
                    cy += wrap(g, r.incumbent + " (" + (r.party == DEM ? "D" : "R") + ")" + (r.open ? " - open seat" : " - running") + (r.note.isEmpty() ? "" : "; " + r.note) + ".", x, cy + 12, w, F_XS, C_TEXT) + 6;
                }
                if (me.nominee) {
                    u.toggle(g, x, cy + 4, me.redBox[s], () -> me.redBox[s] = !me.redBox[s], "Put this state in a 'red box' on your website: allied super PACs read it as a signal to spend here.");
                    text(g, "Red-box it for allied super PACs", x + 44, cy + 18, F_XS, C_TEXT);
                    cy += 32;
                }
            }
            u.button(g, x, cy, w / 2 - 4, 26, "Send candidate", B_NORM, () -> Tabs.quickVisit(u, s, u.mx - 200, u.my - 100), "Schedule an event here in the next free slot.");
            u.button(g, x + w / 2 + 4, cy, w / 2 - 4, 26, "Buy ads here", B_NORM, () -> Hq.rtab = 1);
            return cy + 40;
        }

        static double ads(Battleground2028 u, Graphics2D g, Game game, Camp me, int s, double x, double cy, double w) {
            label(g, "Broadcast TV by media market", x, cy + 12, C_GOLD);
            cy += 16;
            wrap(g, "Weekly gross rating points (100 GRPs = the average viewer sees the ad once). 600-1,000 a week is a heavy buy; 1,500 is saturation. Prices rise toward November.", x, cy + 12, w, F_T, C_DIM);
            cy += 46;
            for (int m : Elect.marketsIn(s)) {
                final int mm = m;
                double cppv = Week.cpp(game, m, true);
                double cost = me.tvGrp[m] * cppv;
                boolean on = me.tvGrp[m] > 0;
                u.card(g, x - 4, cy, w + 8, 74, on, null, null);
                textFit(g, Elect.marketName[m], x + 4, cy + 18, w - 120, F_SB, on ? Color.white : C_TEXT);
                textR(g, money(cppv) + "/pt", x + w - 4, cy + 18, F_T, C_DIM);
                StringBuilder reach = new StringBuilder(compact(Elect.marketHH[m] * 1000) + " homes");
                for (int t : Elect.marketStates[m]) if (Elect.dmaShare[m][t] > 0.02) reach.append("  ").append(STATES[t].ab).append(" ").append(Math.round(Elect.dmaShare[m][t] * 100)).append("%");
                textFit(g, reach.toString(), x + 4, cy + 33, w - 8, F_T, C_FAINT);
                u.stepper(g, x + 4, cy + 42, fmt0(me.tvGrp[m]), () -> me.tvGrp[mm] = Math.max(0, me.tvGrp[mm] - 100), () -> me.tvGrp[mm] = Math.min(2000, me.tvGrp[mm] + 100), "Weekly GRPs in " + Elect.marketName[m], 54);
                u.segmented(g, x + 120, cy + 42, 160, 24, new String[]{"Pos", "Contrast", "Neg"}, me.tvTone[m], i -> me.tvTone[mm] = i,
                        new String[]{"Positive: builds your image. Safe.", "Contrast: you vs them. Effective and moderate risk.", "Negative: defines your opponent. Most powerful - and backlash if overused."});
                textR(g, money(cost), x + w - 4, cy + 60, F_XSB, on ? C_GOLD : C_FAINT);
                cy += 80;
            }
            cy += 4;
            label(g, "Digital in " + STATES[s].name, x, cy + 12, C_GOLD);
            cy += 18;
            double adults = Math.max(1, Week.sumCvap(s));
            double imp = me.digital[s] / Digital.avgCpm(me) * 1000 / adults;
            int topP = 0;
            for (int p = 1; p < NPLAT; p++) if (me.digMix[p] > me.digMix[topP]) topP = p;
            text(g, fmt1(imp) + " impressions per adult per week, mostly " + PLAT[topP] + " (" + DMSG_SHORT[me.digMsg[topP]] + ")", x, cy + 12, F_T, C_DIM);
            cy += 18;
            u.stepper(g, x, cy, money(me.digital[s]), () -> me.digital[s] = Math.max(0, me.digital[s] - 25000), () -> me.digital[s] = Math.min(5e6, me.digital[s] + 25000), "Weekly digital spending in this state (step $25K). Platforms and messages: Money tab.", 90);
            u.button(g, x + 160, cy, 60, 24, "+$100K", B_GHOST, () -> me.digital[s] = Math.min(5e6, me.digital[s] + 100000));
            u.button(g, x + 226, cy, 50, 24, "Clear", B_GHOST, () -> me.digital[s] = 0);
            cy += 36;
            if (me.nominee) {
                double pac = me.superPac[s];
                label(g, "Allied super PACs", x, cy + 12, C_DIM);
                text(g, pac > 1 ? "Your allies are on the air here (" + fmt0(pac) + " GRPs in the pipeline)." : "No allied spending here yet. Red-box the state to signal them.", x, cy + 30, F_T, C_TEXT);
                cy += 40;
            }
            return cy;
        }

        static double field(Battleground2028 u, Graphics2D g, Game game, Camp me, int s, double x, double cy, double w) {
            double idx = Week.stateCostIndex(s);
            label(g, "Field offices", x, cy + 12, C_GOLD);
            text(g, money(12000 * idx) + " to open, " + money(Week.officeWeek(s)) + "/wk", x + w - 170, cy + 12, F_T, C_DIM);
            cy += 18;
            u.stepper(g, x, cy, fmt0(me.offices[s] + me.officeOrder[s]), () -> me.officeOrder[s] = (int) Math.max(-me.offices[s], me.officeOrder[s] - 1), () -> me.officeOrder[s]++, "Each office houses about 8 organisers and anchors the local volunteer program.", 60);
            if (me.officeOrder[s] != 0) text(g, (me.officeOrder[s] > 0 ? "opening " : "closing ") + Math.abs(me.officeOrder[s]), x + 130, cy + 16, F_XSB, C_GOLD);
            cy += 36;
            label(g, "Paid organisers", x, cy + 12, C_GOLD);
            text(g, money(2500) + " to hire, " + money(Week.ORGANIZER_WEEK * idx) + "/wk each", x + w - 190, cy + 12, F_T, C_DIM);
            cy += 18;
            u.stepper(g, x, cy, fmt0(me.organizers[s] + me.organizerOrder[s]), () -> me.organizerOrder[s] = (int) Math.max(-me.organizers[s], me.organizerOrder[s] - 5), () -> me.organizerOrder[s] += 5, "Organisers recruit and manage volunteers. Step: 5.", 60);
            u.button(g, x + 130, cy, 50, 24, "+25", B_GHOST, () -> me.organizerOrder[s] += 25);
            if (me.organizerOrder[s] != 0) text(g, (me.organizerOrder[s] > 0 ? "hiring " : "laying off ") + Math.abs(me.organizerOrder[s]), x + 190, cy + 16, F_XSB, C_GOLD);
            double cap = (me.offices[s] + me.officeOrder[s]) * 8 + 2;
            if (me.organizers[s] + me.organizerOrder[s] > cap) text(g, "Not enough offices: extra organisers work at 40%.", x, cy + 40, F_T, C_AMBER);
            cy += 52;
            label(g, "Transfers to the state party", x, cy + 12, C_GOLD);
            cy += 18;
            u.stepper(g, x, cy, money(me.transferW[s]) + "/wk", () -> me.transferW[s] = Math.max(0, me.transferW[s] - 50000), () -> me.transferW[s] = Math.min(5e6, me.transferW[s] + 50000),
                    "The state party's coordinated campaign hires organisers with your money (with a lag) and helps down-ballot.", 110);
            text(g, "funds ~" + fmt0(me.partyOrg[s]) + " organisers", x + 180, cy + 16, F_T, C_DIM);
            cy += 40;
            label(g, "This week's ground game", x, cy + 12, C_DIM);
            cy += 16;
            String[][] rows = {{"Volunteers on the list", compact(me.volunteers[s])}, {"Volunteer shifts", compact(me.shiftsW[s])}, {"Doors knocked", compact(me.doorsW[s])},
                    {"Phone calls", compact(me.callsW[s])}, {"Texts", compact(me.textsW[s])}, {"Relational contacts", compact(me.relW[s])}, {"Doors to date", compact(me.doorsT[s])}};
            for (String[] r : rows) { text(g, r[0], x, cy + 15, F_XS, C_TEXT); textR(g, r[1], x + w, cy + 15, F_XSB, C_TEXT); cy += 18; }
            cy += 6;
            text(g, "Persuasion reach", x, cy + 12, F_XS, C_TEXT);
            bar(g, x + 120, cy + 5, w - 120, 7, Week.fieldSat(me.persuade[s]) / 0.1, C_TEAL);
            cy += 18;
            text(g, "Get-out-the-vote", x, cy + 12, F_XS, C_TEXT);
            bar(g, x + 120, cy + 5, w - 120, 7, Week.gotvSat(me.gotv[s]) / 0.12, C_GOLD);
            u.hit(x, cy - 18, w, 36, null, "How much of the electorate your volunteers have reached recently. GOTV builds only in the last six weeks before the vote.");
            cy += 26;
            return cy;
        }

        static double voters(Battleground2028 u, Graphics2D g, Game game, Camp me, int s, double x, double cy, double w) {
            if (groupShareState != s) {
                groupShareState = s;
                groupShareCache = new double[NG];
                for (int gr = 0; gr < NG; gr++) groupShareCache[gr] = Vote.groupSize(gr, s);
                issueCache = new double[NI];
                double tw = 0;
                for (int c = 0; c < NCELL; c++) {
                    double n = Elect.cvap[s][c] * Elect.turnB[s][c];
                    for (int i = 0; i < NI; i++) issueCache[i] += n * Elect.sal[s][c][i] * ISSUE_SAL[i];
                    tw += n;
                }
                for (int i = 0; i < NI; i++) issueCache[i] /= tw;
            }
            InternalPoll p = Polls.latest(game, me.idx, s);
            boolean tabs = p != null && p.share == null;
            label(g, "Who votes here" + (tabs ? " - and your latest poll" : ""), x, cy + 12, C_GOLD);
            cy += 18;
            if (!tabs) { text(g, "Poll the state to see how each group is breaking.", x, cy + 10, F_T, C_DIM); cy += 16; }
            for (int gr = 0; gr < NG; gr++) {
                double sz = groupShareCache[gr];
                if (sz < 0.004) continue;
                textFit(g, GROUP[gr], x, cy + 13, 150, F_XS, C_TEXT);
                bar(g, x + 152, cy + 6, 80, 7, sz / 0.6, C_ACCENT);
                text(g, Math.round(sz * 100) + "%", x + 238, cy + 13, F_T, C_DIM);
                if (tabs) {
                    double m = p.groupMargin[gr];
                    textR(g, Double.isNaN(m) ? "n/a" : margin(m), x + w, cy + 13, F_XSB, Double.isNaN(m) ? C_FAINT : m >= 0 ? C_DEM_L : C_REP_L);
                }
                cy += 17;
            }
            cy += 10;
            label(g, "What voters here care about", x, cy + 12, C_GOLD);
            cy += 18;
            Integer[] idx = new Integer[NI];
            for (int i = 0; i < NI; i++) idx[i] = i;
            final double[] ic = issueCache;
            final Game gg = game;
            Arrays.sort(idx, (a, b) -> Double.compare(ic[b] * gg.salience[b], ic[a] * gg.salience[a]));
            double max = ic[idx[0]] * game.salience[idx[0]];
            for (int k = 0; k < 8; k++) {
                int i = idx[k];
                text(g, ISSUE[i], x, cy + 13, F_XS, C_TEXT);
                bar(g, x + 170, cy + 6, w - 170, 7, ic[i] * game.salience[i] / max, C_AMBER);
                cy += 17;
            }
            return cy + 10;
        }

        // ---------------------------------------------------------------- the national picture
        static void national(Battleground2028 u, Graphics2D g, Game game, Camp me, double x, double y, double w, double h) {
            text(g, "The Nation", x, y + 26, F_H1, Color.white);
            text(g, "Click a state on the map to work it.", x, y + 46, F_XS, C_DIM);
            double off = u.beginScroll(g, "right", x - 4, y + 56, w + 8, h - 56);
            int h0 = u.hits.size();
            double cy = y + 60 - off;
            label(g, "The mood of the country", x, cy + 12, C_GOLD);
            cy += 18;
            String[][] rows = {{"Presidential approval", fmt1(game.approval) + "%"}, {"Unemployment", fmt1(game.unemp) + "%"}, {"Inflation", fmt1(game.inflation) + "%"},
                    {"Economic growth", fmt1(game.gdp) + "%"}, {"National environment", margin(game.env)}, {"House", game.demHouse ? "Democrats (" + (int) game.houseD + ")" : "Republicans (" + (435 - (int) game.houseD) + ")"},
                    {"Senate", (int) game.senateD + " D - " + (100 - (int) game.senateD) + " R"}};
            String[] tips = {"The sitting president's job approval. The party in power is judged on it.", null, null, null,
                    "How the fundamentals - approval, the economy, the midterm signal - lean the race, before the candidates.", null, null};
            for (int i = 0; i < rows.length; i++) {
                text(g, rows[i][0], x, cy + 15, F_XS, C_TEXT);
                textR(g, rows[i][1], x + w, cy + 15, F_XSB, i == 4 ? (game.env >= 0 ? C_DEM_L : C_REP_L) : C_TEXT);
                if (tips[i] != null) u.hit(x, cy, w, 18, null, tips[i]);
                cy += 18;
            }
            cy += 12;
            label(g, "What the country cares about", x, cy + 12, C_GOLD);
            cy += 18;
            Integer[] idx = new Integer[NI];
            for (int i = 0; i < NI; i++) idx[i] = i;
            final Game gg = game;
            Arrays.sort(idx, (a, b) -> Double.compare(ISSUE_SAL[b] * gg.salience[b], ISSUE_SAL[a] * gg.salience[a]));
            double max = ISSUE_SAL[idx[0]] * game.salience[idx[0]];
            for (int k = 0; k < 10; k++) {
                int i = idx[k];
                text(g, ISSUE[i], x, cy + 13, F_XS, C_TEXT);
                bar(g, x + 170, cy + 6, w - 170, 7, ISSUE_SAL[i] * game.salience[i] / max, game.newsSal[i] > 0.2 ? C_BAD : C_AMBER);
                if (game.newsSal[i] > 0.2) u.hit(x, cy, w, 17, null, ISSUE[i] + " is in the news this week.");
                cy += 17;
            }
            cy += 12;
            if (Hq.primaryView(game)) {
                Camp other = null;
                label(g, "The other party's race", x, cy + 12, C_GOLD);
                cy += 18;
                int op = me.party == DEM ? REP : DEM;
                List<Camp> cs = game.party(op);
                final double[] pp = game.primaryPoll;
                if (pp != null) {
                    Collections.sort(cs, (a, b) -> Double.compare(pp[b.idx], pp[a.idx]));
                    for (int i = 0; i < Math.min(6, cs.size()); i++) {
                        Camp k = cs.get(i);
                        Portrait.avatar(g, k.c, x + 9, cy + 9, 9, null);
                        text(g, k.name(), x + 24, cy + 13, F_XS, C_TEXT);
                        textR(g, Math.round(pp[k.idx] * 100) + "%" + (k.delegates > 0 ? "  " + fmt0(k.delegates) + " del." : ""), x + w, cy + 13, F_XSB, C_TEXT);
                        cy += 21;
                    }
                }
            } else {
                label(g, "Third parties", x, cy + 12, C_GOLD);
                cy += 18;
                Polls.truth(game);
                for (Camp k : game.camps) {
                    if (k.party < GRN || !k.active) continue;
                    Portrait.avatar(g, k.c, x + 9, cy + 9, 9, null);
                    text(g, k.name() + " (" + PARTY_SHORT[k.party] + ")", x + 24, cy + 13, F_XS, C_TEXT);
                    textR(g, fmt1(game.thirdPoll(k) * 100) + "%", x + w, cy + 13, F_XSB, C_TEXT);
                    cy += 21;
                }
                cy += 8;
                Camp op = game.opp();
                if (op != null) {
                    label(g, "Your opponent", x, cy + 12, C_GOLD);
                    cy += 18;
                    cy = Tabs.rivalCard(u, g, game, me, op, x, cy, w);
                }
            }
            u.clipHits(h0, y + 56, y + h);
            u.endScroll(g, "right", cy + off - y - 56 + 10);
        }
    }

    // =====================================================================
    //  28.  THE DEBATE STAGE
    // =====================================================================
    static final class DebateView {
        static DebateRun run;
        static int shown;                    // lines already revealed
        static double revealT;               // time since the current line appeared
        static int speaker = -1;
        static double[] pop = new double[12];
        static boolean revealing;

        static void start(Battleground2028 u, Debate d) {
            run = Debates.start(u.game, d);
            shown = run.lines.size();
            revealing = false;
            speaker = -1;
            Arrays.fill(pop, 0);
            u.go(SC_DEBATE);
            snd(Sfx.STING);
        }

        static void update(Battleground2028 u, double dt) {
            if (run == null) return;
            if (revealing) {
                revealT += dt;
                for (int i = 0; i < pop.length; i++) pop[i] = Math.max(0, pop[i] - dt * 0.6);
                if (revealT > 1.5) nextLine(u);
            }
        }

        static void nextLine(Battleground2028 u) {
            revealT = 0;
            if (shown >= run.lines.size()) { revealing = false; speaker = -1; return; }
            String l = run.lines.get(shown++);
            speaker = -1;
            for (int i = 0; i < run.parts.size(); i++) if (l.startsWith(Debates.who(run, i).last + " ")) speaker = i;
            if (speaker >= 0 && !run.dial.isEmpty()) {
                double[] row = run.dial.get(run.dial.size() - 1);
                if (speaker < row.length) pop[speaker] = 1;
            }
            if (l.isEmpty() || l.startsWith("Q")) revealT = 1.0;
            if (speaker < 0 && !l.startsWith("Q") && !l.isEmpty()) revealT = 0.3;
        }

        /** Step one: the tactic. Step two (chooseLine) answers with a written line. */
        static void choose(Battleground2028 u, int t) {
            if (run == null || run.over || revealing || run.rebutBy >= 0) return;
            run.pickTactic = t;
            run.options = Debates.lines(u.game, run, t);
            snd(Sfx.CLICK);
        }

        static void chooseLine(Battleground2028 u, int i) {
            if (run == null || run.over || revealing || run.options == null || i < 0 || i >= run.options.length) return;
            Line l = run.options[i];
            run.pickTactic = -1;
            run.options = null;
            Debates.segment(u.game, run, l.tactic, l);
            revealing = true;
            revealT = 1.5;
            snd(Sfx.CLICK);
        }

        static void rebut(Battleground2028 u, int opt) {
            if (run == null || revealing || run.rebutBy < 0) return;
            Debates.rebut(u.game, run, opt);
            revealing = true;
            revealT = 1.5;
            snd(Sfx.CLICK);
        }

        static void key(Battleground2028 u, KeyEvent e) {
            int k = e.getKeyCode();
            if (k >= KeyEvent.VK_1 && k <= KeyEvent.VK_6) {
                int i = k - KeyEvent.VK_1;
                if (run != null && run.rebutBy >= 0 && !revealing) { if (i < 4) rebut(u, i); }
                else if (run != null && run.options != null) chooseLine(u, i);
                else choose(u, i);
            }
            if (k == KeyEvent.VK_ESCAPE && run != null && run.options != null) { run.options = null; run.pickTactic = -1; }
            if (k == KeyEvent.VK_SPACE && revealing) revealT = 2;
        }

        static void draw(Battleground2028 u, Graphics2D g) {
            if (run == null) { u.go(SC_HQ); return; }
            Game game = u.game;
            DebateRun r = run;
            int n = r.parts.size();
            // stage
            g.setPaint(new GradientPaint(0, 0, rgb(0x0b1736), 0, DH, rgb(0x02040a)));
            g.fillRect(0, 0, DW, DH);
            for (int i = 0; i < 7; i++) {
                double cx = 120 + i * 230;
                g.setPaint(new RadialGradientPaint(new Point2D.Double(cx, 40), 420f, new float[]{0f, 1f}, new Color[]{new Color(120, 170, 255, 38), new Color(0, 0, 0, 0)}));
                g.fill(new Rectangle2D.Double(cx - 420, 0, 840, 620));
            }
            // the set: a curved video wall and a glossy floor
            RoundRectangle2D wall = new RoundRectangle2D.Double(120, 150, DW - 240, 300, 40, 40);
            g.setPaint(new GradientPaint(0, 150, new Color(20, 44, 110, 200), 0, 450, new Color(8, 16, 40, 200)));
            g.fill(wall);
            Shape pcl = g.getClip();
            g.clip(wall);
            g.setColor(new Color(255, 255, 255, 12));
            for (int i = 0; i < 9; i++) g.fill(new Rectangle2D.Double(120, 290 + i * 18, DW - 240, 8));
            g.setColor(new Color(255, 255, 255, 30));
            for (int i = 0; i < 78; i++) Portrait.star(g, 150 + (i % 39) * 34 + (i / 39) * 17, 175 + (i / 39) * 26, 5);
            g.setClip(pcl);
            g.setColor(new Color(124, 196, 255, 60));
            g.draw(wall);
            String wallT = run.d.vp ? "THE VICE-PRESIDENTIAL DEBATE" : run.d.party < 0 ? "THE PRESIDENTIAL DEBATE" : "THE " + (run.d.party == DEM ? "DEMOCRATIC" : "REPUBLICAN") + " DEBATE";
            textC(g, wallT, DW / 2, 236, new Font(SERIF, Font.BOLD, 38), new Color(255, 255, 255, 70));
            textC(g, HOSTS[run.d.host].toUpperCase(), DW / 2, 266, F_H3, new Color(255, 255, 255, 60));
            g.setPaint(new GradientPaint(0, 600, new Color(30, 50, 90, 120), 0, DH, new Color(0, 0, 0, 0)));
            g.fill(new Rectangle2D.Double(0, 600, DW, DH - 600));
            // header
            g.setColor(rgb(0xd7263d));
            g.fill(new RoundRectangle2D.Double(40, 28, 54, 24, 6, 6));
            text(g, "LIVE", 51, 45, F_BB, Color.white);
            String title = r.d.vp ? "VICE-PRESIDENTIAL DEBATE" : r.d.party < 0 ? "PRESIDENTIAL DEBATE" : (r.d.party == DEM ? "DEMOCRATIC" : "REPUBLICAN") + " PRIMARY DEBATE";
            text(g, title, 108, 46, F_H2, Color.white);
            text(g, HOSTS[r.d.host] + "  |  " + dateStr(r.d.week * 7 + 2) + (r.d.mutedMics ? "  |  muted microphones" : "") + (r.d.audience ? "  |  live audience" : ""), 110, 66, F_XS, C_DIM);
            // topic banner
            int seg = Math.min((revealing || r.rebutBy >= 0) && r.seg > 0 ? r.seg - 1 : r.seg, r.nSeg - 1);
            String topic = r.over && !revealing ? "CLOSING STATEMENTS" : r.rebutBy >= 0 && !revealing ? "YOUR RESPONSE" : "QUESTION " + (seg + 1) + " OF " + r.nSeg + ":  " + ISSUE[r.topics[seg]].toUpperCase();
            double bw = strW(g, topic, F_H2) + 60;
            g.setColor(new Color(0, 0, 0, 160));
            g.fill(new RoundRectangle2D.Double(DW / 2 - bw / 2, 92, bw, 40, 20, 20));
            g.setColor(C_GOLD);
            g.draw(new RoundRectangle2D.Double(DW / 2 - bw / 2, 92, bw, 40, 20, 20));
            textC(g, topic, DW / 2, 119, F_H2, Color.white);
            // podiums
            double pw = n <= 2 ? 300 : n <= 4 ? 240 : Math.min(170, (DW - 60) / n - 8);
            double portW = n <= 2 ? 250 : n <= 4 ? 200 : pw;
            double portH = portW * 1.2;
            double gap = (DW - n * pw) / (n + 1);
            double baseY = n <= 4 ? 600 : 620;
            for (int i = 0; i < n; i++) {
                Camp k = r.parts.get(i);
                Cand c = Debates.who(r, i);
                double px = gap + i * (pw + gap);
                boolean me = i == r.myIdx;
                boolean talk = i == speaker && revealing;
                // spotlight on the speaker
                if (talk) {
                    g.setPaint(new RadialGradientPaint(new Point2D.Double(px + pw / 2, baseY - portH * 0.6), (float) (portH), new float[]{0f, 1f}, new Color[]{new Color(255, 236, 180, 70), new Color(0, 0, 0, 0)}));
                    g.fill(new Rectangle2D.Double(px - 100, baseY - portH * 1.6, pw + 200, portH * 1.8));
                }
                Portrait.draw(g, c, px + (pw - portW) / 2, baseY - portH - 40, portW, portH, u.tsec + i * 1.7, talk ? 1 : 0.05, false, false);
                // podium
                Path2D pod = new Path2D.Double();
                pod.moveTo(px + 10, baseY - 52); pod.lineTo(px + pw - 10, baseY - 52); pod.lineTo(px + pw - 24, baseY + 120); pod.lineTo(px + 24, baseY + 120); pod.closePath();
                Color pc = Portrait.partyBg(k.party);
                g.setPaint(new GradientPaint((float) px, (float) (baseY - 52), shade(pc, 0.8), (float) px, (float) (baseY + 120), shade(pc, 0.25)));
                g.fill(pod);
                g.setColor(new Color(255, 255, 255, 50));
                g.draw(pod);
                g.setColor(new Color(255, 255, 255, 230));
                g.fill(new RoundRectangle2D.Double(px + 24, baseY - 40, pw - 48, 30, 6, 6));
                textC(g, c.last.toUpperCase(), px + pw / 2, baseY - 19, n > 6 ? F_XSB : F_BB, rgb(0x0b1220));
                if (me) textC(g, "YOU", px + pw / 2, baseY + 10, F_TB, C_GOLD);
                double sc = r.score[i];
                textC(g, (sc >= 0 ? "+" : "") + fmt0(sc), px + pw / 2, baseY + 34, F_H2, sc >= 0 ? C_GOOD : C_BAD);
                if (pop[i] > 0 && !r.dial.isEmpty()) {
                    double d = r.dial.get(r.dial.size() - 1)[Math.min(i, r.dial.get(r.dial.size() - 1).length - 1)];
                    double a = clamp(pop[i], 0, 1);
                    String ds = (d >= 0 ? "+" : "") + fmt0(d);
                    Color dc = alpha(d >= 0 ? C_GOOD : C_BAD, a);
                    if (n <= 4) text(g, ds, px + (pw + portW) / 2 + 6, baseY - portH * 0.7 - (1 - a) * 30, F_H1, dc);     // beside the head, clear of the backdrop
                    else textC(g, ds, px + pw / 2, baseY - portH - 50 - (1 - a) * 30, F_H1, dc);
                }
            }
            // caption of the current line
            double cy = 676;
            if (shown > 0) {
                String l = run.lines.get(Math.max(0, shown - 1));
                if (!l.isEmpty()) {
                    g.setColor(new Color(0, 0, 0, 180));
                    g.fill(new RoundRectangle2D.Double(DW / 2 - 520, cy, 1040, 38, 10, 10));
                    textC(g, l, DW / 2, cy + 25, F_BB, Color.white);
                }
            }
            // dial
            dial(g, r, 40, 728, 520, 150);
            // controls
            if (r.myIdx >= 0 && r.rebutBy >= 0 && !revealing) {
                double tx = 590, ty = 728, tw = (DW - 40 - tx) / 2 - 8;
                label(g, Debates.who(r, r.rebutBy).last + " just came after you - respond (keys 1-4)", tx, ty - 6, C_BAD);
                for (int t = 0; t < REBUT.length; t++) {
                    final int k = t;
                    double bx = tx + (t % 2) * (tw + 8), by = ty + (t / 2) * 76;
                    boolean hov = u.card(g, bx, by, tw, 68, false, () -> rebut(u, k), REBUT_DESC[t]);
                    text(g, (t + 1) + "  " + REBUT[t], bx + 12, by + 24, F_BB, hov ? Color.white : C_TEXT);
                    wrap(g, REBUT_DESC[t], bx + 12, by + 44, tw - 24, F_T, C_DIM);
                }
            } else if (!r.over && r.myIdx >= 0 && r.options != null && !revealing) {
                double tx = 590, ty = 728, tw = DW - 40 - tx;
                label(g, TACTIC[r.pickTactic] + ": choose your answer (keys 1-" + r.options.length + ", Esc to go back)", tx, ty - 6, C_GOLD);
                double bh = Math.min(48, 150.0 / r.options.length - 4);
                for (int i = 0; i < r.options.length; i++) {
                    final int k = i;
                    Line l = r.options[i];
                    double by = ty + i * (bh + 4);
                    boolean hov = u.card(g, tx, by, tw - 96, bh, false, () -> chooseLine(u, k), "Tone: " + l.tag + (l.sd >= 12 ? " - high risk, high reward" : l.sd <= 5 ? " - steady" : ""));
                    chip(g, l.tag.toUpperCase(), tx + 10, by + bh / 2 - 8, l.sd >= 12 ? C_AMBER : l.target >= 0 ? C_BAD : C_ACCENT, false);
                    textFit(g, (i + 1) + "  " + l.text, tx + 112, by + bh / 2 + 5, tw - 230, F_S, hov ? Color.white : C_TEXT);
                }
                u.button(g, DW - 40 - 88, ty, 88, 30, "Back", B_GHOST, () -> { r.options = null; r.pickTactic = -1; });
            } else if (!r.over && r.myIdx >= 0) {
                double tx = 590, ty = 728, tw = (DW - 40 - tx) / 3 - 8;
                label(g, revealing ? "Answering..." : "Choose your approach to this question (keys 1-6)", tx, ty - 6, revealing ? C_DIM : C_GOLD);
                for (int t = 0; t < TACTIC.length; t++) {
                    final int k = t;
                    double bx = tx + (t % 3) * (tw + 8), by = ty + (t / 3) * 76;
                    double sk = Debates.skill(r, r.myIdx, t) + Math.min(40, game.me().prep) * 0.28;
                    boolean hov = u.card(g, bx, by, tw, 68, false, revealing ? null : () -> choose(u, k), TACTIC_DESC[t]);
                    text(g, (t + 1) + "  " + TACTIC[t], bx + 12, by + 22, F_BB, revealing ? C_FAINT : hov ? Color.white : C_TEXT);
                    bar(g, bx + 12, by + 34, tw - 24, 6, sk / 110.0, sk > 85 ? C_GOOD : sk > 65 ? C_AMBER : C_BAD);
                    String hint = t == 0 ? "beats a pivot" : t == 1 ? (r.attacks[r.myIdx] >= 2 ? "overused - backlash!" : "beats a policy answer") : t == 2 ? "beats an attack" : t == 3 ? "blunts an attack" : t == 4 ? (game.me().prep < 20 ? "needs more prep!" : (r.zingers[r.myIdx] >= 1 ? "already used one" : "high risk, high reward")) : "blunts an attack";
                    text(g, hint, bx + 12, by + 58, F_T, hint.contains("!") ? C_BAD : C_DIM);
                }
            } else if (r.over && !revealing && r.rebutBy < 0) {
                results(u, g, r);
            } else if (r.myIdx < 0 && !r.over) {
                u.button(g, DW - 300, 800, 260, 44, "Watch the next question", B_PRIMARY, () -> { Debates.segment(game, r, -1); revealing = true; revealT = 1.5; });
            }
            if (revealing) u.button(g, DW - 140, 28, 100, 28, "Skip", B_GHOST, () -> { while (shown < run.lines.size()) nextLine(u); revealing = false; });
        }

        static void dial(Graphics2D g, DebateRun r, double x, double y, double w, double h) {
            label(g, "Focus group dial", x, y - 6, C_DIM);
            int n = r.parts.size();
            List<double[]> ser = new ArrayList<double[]>();
            Color[] cols = new Color[n];
            String[] names = new String[n];
            double lo = -10, hi = 10;
            for (int i = 0; i < n; i++) {
                double[] s = new double[Math.max(2, r.dial.size() + 1)];
                double acc = 0;
                for (int j = 0; j < r.dial.size(); j++) { acc += r.dial.get(j)[i]; s[j + 1] = acc; }
                if (r.dial.isEmpty()) s[1] = 0;
                for (double v : s) { lo = Math.min(lo, v - 2); hi = Math.max(hi, v + 2); }
                ser.add(s);
                Camp k = r.parts.get(i);
                cols[i] = n <= 2 ? partyLight(k.party) : USMap.CAND_COL[i % USMap.CAND_COL.length];
                names[i] = Debates.who(r, i).last;
            }
            chart(g, x + 24, y, w - 24, h, ser, cols, n <= 6 ? names : null, lo, hi, "%+.0f", r.nSeg + 1);
        }

        static void results(Battleground2028 u, Graphics2D g, DebateRun r) {
            double x = 590, y = 724, w = DW - 40 - x;
            u.panel(g, x, y, w, 156, "The verdict");
            wrap(g, r.headline == null ? "" : r.headline, x + 16, y + 48, w - 330, F_H3, Color.white);
            int best = 0;
            for (int i = 1; i < r.parts.size(); i++) if (r.score[i] > r.score[best]) best = i;
            wrap(g, (best == r.myIdx ? "You won the night. Expect a bump in the polls, a fundraising surge and a fired-up base." : r.myIdx >= 0 ? "Not your night. " + Debates.who(r, best).last + " dominated the coverage; time to change the subject." : Debates.who(r, best).last + " won the night."),
                    x + 16, y + 92, w - 330, F_S, C_DIM);
            u.button(g, x + w - 290, y + 96, 270, 44, "Back to headquarters", B_GOLD, () -> {
                run = null;
                u.go(SC_HQ);
                Hq.endWeek(u);
            });
        }
    }

    // =====================================================================
    //  29.  ELECTION NIGHT AND THE AFTERMATH
    // =====================================================================
    static final class Night {
        static double clock;                 // hours after 6 p.m. Eastern
        static double speed = 1;             // hours of election night per real minute x 60
        static boolean[] called = new boolean[NS];
        static double[] callTime = new double[NS];
        static int evD, evR, evI;
        static int[] win = new int[NS];
        static List<String> ticker = new ArrayList<String>();
        static boolean done, finale;
        static double finaleT;
        static double[] finalM = new double[NS];
        static int sel = -1;
        static double lastSound;

        static void start(Battleground2028 u) {
            Game g = u.game;
            clock = 0.6;
            speed = 1;
            Arrays.fill(called, false);
            Arrays.fill(callTime, -1);
            evD = evR = evI = 0;
            ticker.clear();
            done = false; finale = false;
            for (int s = 0; s < NS; s++) { finalM[s] = Vote.margin(g.result[s]); win[s] = Results.winner(g, s); }
            USMap.cur = new Color[NS];
            for (int s = 0; s < NS; s++) USMap.cur[s] = C_STATE;
            u.go(SC_NIGHT);
            snd(Sfx.STING);
        }

        static double closeHour(int s) { return STATES[s].close - 18; }

        static double tau(int s) { int sp = STATES[s].countSpeed; return sp == 1 ? 0.55 : sp == 2 ? 1.0 : 2.4; }

        /** Share of the vote counted. */
        static double reported(int s) {
            double t = clock - closeHour(s);
            if (t <= 0) return 0;
            double r = 1 - Math.exp(-t / tau(s));
            return Math.min(0.995, r * 1.02);
        }

        static boolean mailLate(int s) { String ab = STATES[s].ab; return "PA WI MI NY".contains(ab); }

        /** The margin of the votes counted so far: the order of counting creates red mirages and blue shifts. */
        static double[] counted(Game g, int s) {
            double r = reported(s);
            double[] e = g.banked[s], tot = g.result[s];
            double te = e[0] + e[1] + e[2], tt = tot[0] + tot[1] + tot[2];
            double td = Math.max(1, tt - te);
            double want = r * tt;
            double[] out = new double[3];
            boolean late = mailLate(s);
            double first = late ? td : te, second = late ? te : td;
            double[] fv = new double[3], sv = new double[3];
            for (int j = 0; j < 3; j++) { double ev = e[j], dv = tot[j] - e[j]; fv[j] = late ? dv : ev; sv[j] = late ? ev : dv; }
            if (want <= first) for (int j = 0; j < 3; j++) out[j] = fv[j] * want / Math.max(1, first);
            else for (int j = 0; j < 3; j++) out[j] = fv[j] + sv[j] * (want - first) / Math.max(1, second);
            return out;
        }

        static double needed(int s) {
            double m = Math.abs(finalM[s]);
            if (win[s] == IND) m = 4;
            return m > 15 ? 0 : m > 9 ? 0.08 : m > 5 ? 0.35 : m > 3 ? 0.6 : m > 1.5 ? 0.82 : m > 0.6 ? 0.94 : 0.985;
        }

        static void update(Battleground2028 u, double dt) {
            Game g = u.game;
            if (g == null || g.result == null) return;
            if (finale) { finaleT += dt; return; }
            clock += dt * speed / 60.0 * 9;     // at 1x, an hour of results every ~7 seconds
            for (int s = 0; s < NS; s++) {
                if (called[s] || clock < closeHour(s)) continue;
                if (reported(s) >= needed(s) || clock > 9.5) call(u, g, s);
            }
            if (!done && clock > 9.6) {
                for (int s = 0; s < NS; s++) if (!called[s]) call(u, g, s);
                done = true;
            }
            if ((evD >= 270 || evR >= 270 || evI >= 270 || done) && !finale) {
                finale = true;
                finaleT = 0;
                Camp me = g.me();
                boolean won = me != null && !g.spectator && me.party == General.winnerParty(g) && (evD >= 270 || evR >= 270 || evI >= 270 || done);
                snd(won ? Sfx.CHEER : Sfx.BELL);
            }
        }

        static void call(Battleground2028 u, Game g, int s) {
            called[s] = true;
            callTime[s] = clock;
            int wp = win[s];
            int ev = STATES[s].ev;
            if (STATES[s].ab.equals("ME") || STATES[s].ab.equals("NE")) {
                ev = 2;
                for (int k = 0; k < CD_DATA.length; k++) if ((int) CD_DATA[k][0] == s) { if (g.cdWinner[k] == DEM) evD++; else if (g.cdWinner[k] == IND) evI++; else evR++; }
            }
            if (wp == DEM) evD += ev; else if (wp == REP) evR += ev; else evI += ev;
            Camp w = wp == DEM ? g.nominee(DEM) : wp == REP ? g.nominee(REP) : g.indep();
            ticker.add(0, timeStr(clock) + "  " + (w != null ? w.last() : PARTY[wp]) + " wins " + STATES[s].name + (wp != IND && Math.abs(finalM[s]) < 2 ? " (close)" : ""));
            if (u.tsec - lastSound > 0.25 && STATES[s].ev >= 6) { snd(Sfx.CALL, 0.6, wp == DEM ? 1.1 : wp == REP ? 0.9 : 1.0); lastSound = u.tsec; }
        }

        static String timeStr(double c) {
            int h = (int) Math.floor(18 + c);
            int m = (int) Math.round((18 + c - h) * 60);
            if (m == 60) { h++; m = 0; }
            int hh = h % 12 == 0 ? 12 : h % 12;
            return hh + ":" + (m < 10 ? "0" : "") + m + (h % 24 < 12 ? " a.m." : " p.m.");
        }

        static void key(Battleground2028 u, KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_SPACE) speed = speed >= 16 ? 1 : speed * 4;
            if (e.getKeyCode() == KeyEvent.VK_ESCAPE) clock = 10;
        }

        static void draw(Battleground2028 u, Graphics2D g) {
            Game game = u.game;
            u.backdrop(g);
            Camp d = game.nominee(DEM), r = game.nominee(REP);
            // header: EV race
            text(g, "ELECTION NIGHT 2028", 40, 44, F_H1, Color.white);
            text(g, timeStr(clock) + " Eastern", 40, 66, F_B, C_GOLD);
            double bx = 380, bw = 840, by = 22;
            g.setColor(new Color(255, 255, 255, 14));
            g.fill(new RoundRectangle2D.Double(bx, by, bw, 34, 10, 10));
            double dw = bw * evD / 538.0, rw = bw * evR / 538.0, iw = bw * evI / 538.0;
            g.setColor(C_DEM);
            g.fill(new RoundRectangle2D.Double(bx, by, dw, 34, 10, 10));
            g.setColor(C_REP);
            g.fill(new RoundRectangle2D.Double(bx + bw - rw, by, rw, 34, 10, 10));
            if (evI > 0) {
                g.setColor(C_IND);
                g.fill(new RoundRectangle2D.Double(bx + dw + 2, by, iw, 34, 6, 6));
                Camp ic = game.indep();
                textC(g, (ic != null ? ic.last() : "Ind.") + " " + evI, bx + dw + 2 + iw / 2, by + 50, F_XSB, C_IND);
            }
            g.setColor(Color.white);
            g.fill(new Rectangle2D.Double(bx + bw * 270 / 538.0 - 1, by - 6, 2, 46));
            textC(g, "270 TO WIN", bx + bw * 270 / 538.0, by + 54, F_TB, C_TEXT);
            if (d != null) { Portrait.avatar(g, d.c, bx - 30, by + 17, 22, C_DEM_L); text(g, evD + "", bx + 10, by + 25, F_H2, Color.white); text(g, d.last(), bx, by + 54, F_SB, C_DEM_L); }
            if (r != null) { Portrait.avatar(g, r.c, bx + bw + 30, by + 17, 22, C_REP_L); textR(g, evR + "", bx + bw - 10, by + 25, F_H2, Color.white); textR(g, r.last(), bx + bw, by + 54, F_SB, C_REP_L); }
            // popular vote counted so far
            double cd = 0, cr = 0, co = 0;
            for (int s = 0; s < NS; s++) { double[] c = counted(game, s); cd += c[0]; cr += c[1]; co += c[2]; }
            double ct = Math.max(1, cd + cr + co);
            textC(g, "Votes counted: " + compact(ct) + "   " + (d != null ? d.last() : "D") + " " + fmt1(cd / ct * 100) + "%  -  " + (r != null ? r.last() : "R") + " " + fmt1(cr / ct * 100) + "%", bx + bw / 2, by + 74, F_XS, C_DIM);
            // speed controls
            double sx = DW - 300;
            u.segmented(g, sx, 22, 260, 30, new String[]{"1x", "4x", "16x", "End"}, speed <= 1 ? 0 : speed <= 4 ? 1 : speed <= 16 ? 2 : 3, i -> { if (i == 3) clock = 10; else speed = i == 0 ? 1 : i == 1 ? 4 : 16; }, null);
            // map
            double mx = 40, my = 110, mw = 1060, mh = 640;
            double[] f = USMap.fit(mx, my, mw, mh);
            for (int s = 0; s < NS; s++) {
                Color c;
                double rep = reported(s);
                if (called[s]) {
                    double age = clock - callTime[s];
                    c = win[s] == IND ? C_IND : finalM[s] > 0 ? (Math.abs(finalM[s]) > 8 ? C_DEM_D : C_DEM) : (Math.abs(finalM[s]) > 8 ? C_REP_D : C_REP);
                    if (age < 0.25) c = mix(Color.white, c, age / 0.25);
                } else if (rep > 0.01) {
                    double[] cc = counted(game, s);
                    double m = (cc[0] - cc[1]) / Math.max(1, cc[0] + cc[1] + cc[2]) * 100;
                    c = mix(C_STATE, m > 0 ? C_DEM_L : C_REP_L, 0.45);
                } else if (clock >= closeHour(s)) c = rgb(0x5d5a72);
                else c = rgb(0x1e2638);
                USMap.cur[s] = c;
            }
            fitDraw(u, g, f);
            // side panel: battlegrounds
            double px = 1120, pw = DW - 40 - px;
            u.panel(g, px, 110, pw, 420, "Key states");
            Integer[] idx = new Integer[NS];
            for (int s = 0; s < NS; s++) idx[s] = s;
            Arrays.sort(idx, (a, b) -> Double.compare(Math.abs(finalM[a]) - STATES[a].ev * 0.05, Math.abs(finalM[b]) - STATES[b].ev * 0.05));
            double yy = 146;
            for (int k = 0; k < 14; k++) {
                int s = idx[k];
                double[] cc = counted(game, s);
                double tot = cc[0] + cc[1] + cc[2];
                double m = tot > 0 ? (cc[0] - cc[1]) / tot * 100 : 0;
                double rep = reported(s);
                text(g, STATES[s].name, px + 14, yy, F_XS, C_TEXT);
                text(g, clock < closeHour(s) ? "polls open" : Math.round(rep * 100) + "% in", px + 140, yy, F_T, C_DIM);
                if (called[s]) chip(g, win[s] == IND ? "I WIN" : finalM[s] > 0 ? "D WIN" : "R WIN", px + pw - 70, yy - 12, win[s] == IND ? C_IND : finalM[s] > 0 ? C_DEM : C_REP, true);
                else if (rep > 0) textR(g, margin(m), px + pw - 14, yy, F_XSB, m >= 0 ? C_DEM_L : C_REP_L);
                yy += 27;
            }
            u.panel(g, px, 540, pw, 210, "Projections");
            for (int i = 0; i < Math.min(8, ticker.size()); i++) text(g, ticker.get(i), px + 14, 576 + i * 21, F_XS, i == 0 ? Color.white : C_DIM);
            // senate
            if (done || finale) {
                int sd = General.senateSeats(game);
                textC(g, "Senate: " + sd + " D - " + (100 - sd) + " R", DW / 2, 790, F_H3, C_TEXT);
            }
            if (finale) finaleCard(u, g, game);
        }

        static void fitDraw(Battleground2028 u, Graphics2D g, double[] f) {
            AffineTransform at = g.getTransform();
            g.translate(f[1], f[2]);
            g.scale(f[0], f[0]);
            for (int s = 0; s < NS; s++) {
                if (USMap.shape[s] == null) continue;
                g.setColor(USMap.cur[s]);
                g.fill(USMap.shape[s]);
            }
            g.setColor(new Color(6, 10, 20, 230));
            g.setStroke(new BasicStroke((float) (0.9 / f[0])));
            for (int s = 0; s < NS; s++) if (USMap.shape[s] != null) g.draw(USMap.shape[s]);
            g.setTransform(at);
            for (int s = 0; s < NS; s++) {
                if (USMap.shape[s] == null || USMap.isCallout(s)) continue;
                double lx = f[1] + STATES[s].cx * f[0], ly = f[2] + STATES[s].cy * f[0];
                if (USMap.bounds[s].getWidth() * f[0] > 30) textC(g, STATES[s].ab, lx, ly + 4, F_TB, new Color(255, 255, 255, 200));
            }
            for (int i = 0; i < CALLOUTS.length; i++) {
                int s = stateIdx(CALLOUTS[i]);
                double[] b = USMap.calloutBox(f, i);
                g.setColor(USMap.cur[s]);
                g.fill(new RoundRectangle2D.Double(b[0], b[1], b[2], b[3], 6, 6));
                text(g, CALLOUTS[i], b[0] + 5, b[1] + 13.5, F_TB, Color.white);
            }
            g.setStroke(new BasicStroke(1f));
        }

        static void finaleCard(Battleground2028 u, Graphics2D g, Game game) {
            double a = Math.min(1, finaleT * 1.5);
            g.setColor(new Color(0, 0, 0, (int) (150 * a)));
            g.fillRect(0, 0, DW, DH);
            int wp = General.winnerParty(game);
            Camp win = wp == IND ? game.indep() : game.nominee(wp);
            if (win == null) return;
            Camp me = game.me();
            boolean mine = me != null && win == me && !game.spectator;
            double w = 820, h = 420, x = (DW - w) / 2, y = (DH - h) / 2 + (1 - easeOut(a)) * 40;
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) a));
            RoundRectangle2D rr = new RoundRectangle2D.Double(x, y, w, h, 20, 20);
            g.setPaint(new GradientPaint((float) x, (float) y, shade(Portrait.partyBg(win.party), 0.6), (float) x, (float) (y + h), rgb(0x070b14)));
            g.fill(rr);
            g.setColor(C_GOLD);
            g.setStroke(new BasicStroke(2f));
            g.draw(rr);
            Portrait.draw(g, win.c, x + 30, y + 40, 280, 336, u.tsec, 0.3, false, true);
            label(g, "PROJECTED WINNER", x + 340, y + 70, C_GOLD);
            text(g, win.name(), x + 340, y + 118, F_DISPLAY, Color.white);
            text(g, "elected the " + (win.c.custom ? "next" : "48th") + " President of the United States", x + 340, y + 146, F_SERIF_S, C_TEXT);
            int wev = wp == DEM ? game.evD : wp == REP ? game.evR : game.evI;
            text(g, game.contingent >= 0 ? "Chosen by the House of Representatives" : wev + " electoral votes (projected)", x + 340, y + 196, game.contingent >= 0 ? F_H2 : F_H1, C_GOLD);
            double tot = game.popD + game.popR + game.popO;
            String pv = "Popular vote: " + fmt1(game.popD / tot * 100) + "% D - " + fmt1(game.popR / tot * 100) + "% R";
            if (game.popI > 0) pv += " - " + fmt1(game.popI / tot * 100) + "% I";
            double oth = game.popO - game.popI;
            if (oth > 0) pv += " - " + fmt1(oth / tot * 100) + "% other";
            text(g, pv, x + 340, y + 226, F_B, C_TEXT);
            String verdict = game.spectator ? "You watched from the sidelines." : mine ? "YOU WON. Your campaign made history." : "You came up short. Concede graciously - there's always 2032.";
            if (game.contingent >= 0) verdict = "No one reached 270. " + verdict;
            wrap(g, verdict, x + 340, y + 264, w - 370, F_H3, mine ? C_GOOD : C_DIM);
            g.setComposite(AlphaComposite.SrcOver);
            if (mine) confetti(g, u.tsec, finaleT);
            u.button(g, x + w - 260, y + h - 64, 230, 44, "The morning after", B_GOLD, () -> u.go(SC_END));
        }

        static void confetti(Graphics2D g, double t, double age) {
            Random r = new Random(7);
            for (int i = 0; i < 160; i++) {
                double x0 = r.nextDouble() * DW, sp = 60 + r.nextDouble() * 120;
                double yy = -20 + ((age * sp + r.nextDouble() * 900) % 940);
                double xx = x0 + Math.sin(t * 2 + i) * 20;
                Color c = i % 3 == 0 ? C_DEM : i % 3 == 1 ? C_REP : Color.white;
                g.setColor(c);
                AffineTransform at = g.getTransform();
                g.translate(xx, yy);
                g.rotate(t * 3 + i);
                g.fill(new Rectangle2D.Double(-4, -2, 8, 4));
                g.setTransform(at);
            }
        }

        // ---------------------------------------------------------------- the morning after
        static void end(Battleground2028 u, Graphics2D g) {
            Game game = u.game;
            u.backdrop(g);
            Camp me = game.me();
            Camp d = game.nominee(DEM), r = game.nominee(REP);
            boolean won = me != null && !game.spectator && game.result != null && me.party == General.winnerParty(game);
            text(g, game.result == null ? "The Campaign Is Over" : won ? "Victory" : "The Morning After", 48, 62, F_DISPLAY, won ? C_GOLD : Color.white);
            if (game.result != null && d != null && r != null)
                text(g, d.name() + " " + game.evD + "  -  " + r.name() + " " + game.evR + (game.evI > 0 && game.indep() != null ? "  -  " + game.indep().name() + " " + game.evI : "") + "  electoral votes", 50, 90, F_B, C_DIM);
            if (game.result != null) {
                double[] f = USMap.fit(40, 110, 820, 520);
                Results.prepare(game);
                for (int s = 0; s < NS; s++) USMap.cur[s] = Results.resultColour(game, s);
                fitDraw(u, g, f);
                // tipping point and closest states
                double[] m = new double[NS];
                for (int s = 0; s < NS; s++) m[s] = Vote.margin(game.result[s]);
                boolean dWon = game.evD > game.evR;
                Integer[] idx = new Integer[NS];
                for (int s = 0; s < NS; s++) idx[s] = s;
                final double[] mm = m;
                Arrays.sort(idx, (a, b) -> dWon ? Double.compare(mm[b], mm[a]) : Double.compare(mm[a], mm[b]));
                int acc = 0, tip = -1;
                for (int s : idx) { acc += STATES[s].ev; if (acc >= 270) { tip = s; break; } }
                double y = 660;
                if (tip >= 0) text(g, "Tipping-point state: " + STATES[tip].name + " (" + margin(m[tip]) + ")", 48, y, F_BB, C_GOLD);
                Arrays.sort(idx, (a, b) -> Double.compare(Math.abs(mm[a]), Math.abs(mm[b])));
                StringBuilder b = new StringBuilder("Closest: ");
                for (int i = 0; i < 6; i++) b.append(STATES[idx[i]].ab).append(" ").append(margin(m[idx[i]])).append(i < 5 ? ",  " : "");
                text(g, b.toString(), 48, y + 24, F_S, C_TEXT);
                StringBuilder fl = new StringBuilder("Flipped from 2024: ");
                int nf = 0;
                for (int s = 0; s < NS; s++) {
                    double m24 = STATES[s].d24 - STATES[s].r24;
                    if ((m24 > 0) != (m[s] > 0)) { fl.append(nf++ > 0 ? ", " : "").append(STATES[s].ab); }
                }
                if (nf == 0) fl.append("none");
                text(g, fl.toString(), 48, y + 46, F_S, C_TEXT);
                int sd = General.senateSeats(game);
                text(g, "Senate: " + sd + " D - " + (100 - sd) + " R" + (sd >= 51 ? " (Democratic control)" : sd == 50 ? " (50-50: the vice president decides)" : " (Republican control)"), 48, y + 68, F_S, C_TEXT);
            }
            // your campaign
            double px = 900, pw = DW - 48 - px;
            u.panel(g, px, 110, pw, 640, "Your campaign by the numbers");
            if (me != null) {
                Portrait.draw(g, me.c, px + 20, 140, 150, 180, u.tsec, 0, false, true);
                text(g, me.name(), px + 190, 170, F_H2, Color.white);
                text(g, game.spectator ? "Lost the nomination" : me.nominee ? PARTY[me.party] + " nominee" : "Primary candidate", px + 190, 192, F_S, C_DIM);
                double drs = 0, vol = 0;
                int visited = 0;
                for (int s = 0; s < NS; s++) { drs += me.doorsT[s]; vol = Math.max(vol, me.volunteers[s]); if (me.spendState[s] > 0) visited++; }
                double peakVol = 0;
                for (double v : me.volHist) peakVol = Math.max(peakVol, v);
                String[][] rows = {{"Raised", money(me.raised)}, {"Spent", money(me.spent)}, {"Small-dollar share", Math.round(100 * me.raisedBy[RS_SMALL] / Math.max(1, me.raised)) + "%"},
                        {"Doors knocked", compact(drs)}, {"Peak volunteers", compact(peakVol)}, {"States with spending", String.valueOf(visited)},
                        {"Promises made", String.valueOf(me.promises.size())}, {"Endorsements", String.valueOf(me.endorsements.size())}, {game.set.mode == MODE_FULL && me.party <= REP ? "Delegates won" : "Debates on stage", game.set.mode == MODE_FULL && me.party <= REP ? fmt0(me.delegates) : String.valueOf(debatesDone(game, me))}};
                double yy = 350;
                for (String[] rw : rows) {
                    text(g, rw[0], px + 20, yy, F_S, C_TEXT);
                    textR(g, rw[1], px + pw - 20, yy, F_BB, Color.white);
                    g.setColor(new Color(255, 255, 255, 14));
                    g.draw(new Line2D.Double(px + 20, yy + 8, px + pw - 20, yy + 8));
                    yy += 28;
                }
                if (game.result != null && game.nominee(DEM) != null && game.nominee(REP) != null) {
                    yy += 6;
                    label(g, "How groups voted (D minus R)", px + 20, yy, C_GOLD);
                    yy += 8;
                    int[] gs = {0, 1, 4, 5, 8, 9, 10, 12, 16, 21, 22, 24};
                    for (int i = 0; i < gs.length; i++) {
                        double gm = groupCache(game, gs[i]);
                        double gx = px + 20 + (i % 2) * (pw / 2 - 10), gy = yy + 16 + (i / 2) * 18;
                        textFit(g, GROUP[gs[i]], gx, gy, 150, F_XS, C_TEXT);
                        textR(g, margin(gm), gx + pw / 2 - 40, gy, F_XSB, gm >= 0 ? C_DEM_L : C_REP_L);
                    }
                }
            }
            u.button(g, 48, 832, 220, 44, "Main menu", B_GHOST, () -> u.go(SC_HOME));
            if (game.result != null) u.button(g, DW / 2 - 150, 832, 300, 44, "Explore the results", B_PRIMARY, () -> Results.open(u), "State by state, how every group swung since 2024, turnout and Congress.");
            u.button(g, DW - 48 - 260, 832, 260, 44, "New campaign", B_GOLD, () -> { u.setup = new Settings(); u.go(SC_SETUP); });
        }

        static int debatesDone(Game g, Camp me) { int n = 0; for (Debate d : g.debates) if (d.done && d.party < 0 && !d.vp && d.participants.contains(me.idx)) n++; return n; }

        static double[] gcache = new double[NG];
        static Game gcacheFor;

        static double groupCache(Game game, int gr) {
            if (gcacheFor != game) { gcacheFor = game; for (int i = 0; i < NG; i++) gcache[i] = Vote.groupMargin(game, i, -1); }
            return gcache[gr];
        }
    }

    // =====================================================================
    //  30.  SAVING AND LOADING; THE BRIEFING TEXT
    // =====================================================================
    static final class SaveGame {
        static final String AUTO = "autosave", SLOT = "save1";

        static File dir() {
            File d = new File(System.getProperty("user.home"), ".battleground2028");
            if (!d.exists()) d.mkdirs();
            return d;
        }

        static File file(String name) { return new File(dir(), name + ".sav"); }

        static boolean exists(String name) { try { return file(name).exists(); } catch (Throwable t) { return false; } }

        static String describe(String name) {
            try {
                File f = new File(dir(), name + ".txt");
                if (!f.exists()) return "";
                BufferedReader r = new BufferedReader(new FileReader(f));
                String s = r.readLine();
                r.close();
                return s == null ? "" : s;
            } catch (Throwable t) { return ""; }
        }

        static void save(Battleground2028 u, String name) {
            Game g = u.game;
            if (g == null) return;
            try {
                ObjectOutputStream o = new ObjectOutputStream(new java.util.zip.GZIPOutputStream(new FileOutputStream(file(name))));
                o.writeObject(g);
                o.close();
                Camp me = g.me();
                FileWriter w = new FileWriter(new File(dir(), name + ".txt"));
                w.write((me != null ? me.last() + " (" + PARTY_SHORT[me.party] + ")" : "Campaign") + ", " + MONTHS[dateOf(g.week * 7)[1]] + " " + dateOf(g.week * 7)[0]);
                w.close();
                if (name.equals(SLOT)) u.toast("Game saved.", C_GOOD);
            } catch (Throwable t) {
                if (name.equals(SLOT)) u.toast("Could not save: " + t.getMessage(), C_BAD);
            }
        }

        static void load(Battleground2028 u, String name) {
            try {
                ObjectInputStream in = new ObjectInputStream(new java.util.zip.GZIPInputStream(new FileInputStream(file(name))));
                Game g = (Game) in.readObject();
                in.close();
                if (g.version < Game.VERSION) {
                    u.info("An older save", "This game was saved by an earlier version of Battleground 2028 and can't be continued in this one. Start a new campaign to try the new features.");
                    return;
                }
                Elect.init();
                g.dirty = true;
                Polls.cacheWeek = -1;
                u.game = g;
                u.setup = g.set;
                Hq.tab = 0; Hq.rtab = 0; Hq.pickSlot = -1; Hq.skip = 0; Hq.resolving = false; Hq.fastForward = false;
                if (g.spectator && g.phase != PH_OVER) Hq.fastForward = true;
                Hq.sel = g.me() != null && g.me().c.home >= 0 ? g.me().c.home : -1;
                Hq.mapMode = g.phase <= PH_PRIMARY ? MM_CAL : MM_PUBLIC;
                Hq.primCacheWeek = -1;
                if (g.phase == PH_OVER && g.result != null) { Night.start(u); return; }
                u.go(SC_HQ);
                u.toast("Welcome back. " + weekStr(g.week) + ".", C_ACCENT);
                Hq.showPending(u);
            } catch (Throwable t) {
                u.info("Could not load", "The saved game could not be read (" + t.getClass().getSimpleName() + "). It may be from an older version.");
            }
        }
    }

    static final class Briefing {
        static final String[][] TEXT = {
                // How to play
                {"Battleground 2028 puts you in charge of a presidential campaign. In a full campaign you begin the week after the November 2026 midterms as one of the party's possible candidates. You must survive the invisible primary (money, staff, endorsements, debates), win delegates in the primaries and caucuses, pick a running mate, survive the conventions and then beat the other party's nominee in the general election on November 7, 2028. Before you start you can shape the field: keep candidates out of either primary, or lock in the other party's nominee. Or run as an independent (see 'Running as an independent').",
                        "#The screen",
                        "The top bar shows your cash, your standing (primary polls and delegates, or the national polls and electoral votes), your national name recognition (hover for your best and worst states), your base's enthusiasm and your candidate's stamina. END WEEK resolves the turn; in the final two weeks it becomes END DAY.",
                        "The left panel holds seven tabs: Schedule, Money, Field, Message, Polls, Campaign and News. The map sits in the middle with five views (six during the primaries). The right panel shows the selected state - or the nation when nothing is selected.",
                        "#A good first week",
                        "Fill the schedule (or press Auto-fill), pick a message frame on the Message tab, commission a poll in an early state, and open a field office or two where the first contests are. Then end the week and read the news.",
                        "#The tutorial",
                        "Tutorial: Learn to Play on the main menu (or the Guided tutorial switch when you set up a campaign) gives you a tour of headquarters on your first turn and a tip the first time you reach each part of the game. In a campaign, Take the guided tour on this screen replays the tour.",
                        "#Staff",
                        "Any part of the campaign can be handed to your staff (Campaign tab): they will schedule, buy ads, run the field program, poll and pick the message for you. Use it to focus on what you enjoy.",
                        "#Keys",
                        "Space: end the week.  1-7: switch tabs.  Esc: deselect / cancel.  F1: this briefing.  Right-click a state: send the candidate there."},
                // The week
                {"Each turn is one week. Your candidate has 21 slots - morning, afternoon and evening for seven days. Events in a state (rallies, town halls, retail stops, union halls, churches, campuses, community events, party dinners, fundraisers) happen where you put them; podcasts, TV interviews, call time and debate prep can be done from anywhere.",
                        "Travel matters: a long same-day flight makes an event less effective and wears the candidate down; travel overnight instead (schedule the far state in the morning). Every event costs stamina - a tired candidate is more likely to make a gaffe. Rest restores stamina.",
                        "Rallies build enthusiasm, recruit volunteers and grow your email list. Town halls persuade but risk unscripted moments. Retail politics is king in Iowa, New Hampshire and South Carolina. Podcasts and long-form interviews reach young men and low-information voters; crossover media (Fox for Democrats, CNN for Republicans) wins swing voters at a risk. Faith, union, campus and community events move specific groups.",
                        "Podcasts are real shows. The biggest (Joe Rogan, Theo Von, Flagrant, Call Her Daddy and others) only book candidates voters have heard of: about 35% national name recognition for the top tier and 15% for mid-size shows; hosts far from your politics may not book you at all. Each appearance reports its views, reviews and the small donors it brings in. Rallies report their crowds - which grow with name recognition, enthusiasm and momentum - and the Campaign tab keeps the record.",
                        "In the general election your surrogates and running mate campaign too: assign them states on the Schedule tab.",
                        "#The final two weeks",
                        "With fourteen days to go, each turn becomes a single day: what you do on Tuesday is resolved before you plan Wednesday. Pick a closing argument when the stretch begins; it builds every day you stick with it, and switching costs you most of what it has built.",
                        "When the world intervenes - a mass shooting, a crisis in the Taiwan Strait, AI layoffs, a hurricane - you will be asked how to respond. Your answer moves your platform and specific voter groups."},
                // Money
                {"Money comes from small-dollar donors (your email and text list - it grows with rallies, viral moments and digital ads), from big donors (fundraisers and bundler meetings, which tap them out over time), from self-funding, from allied groups and, after you win the nomination, from the party.",
                        "TV is bought by media market in gross rating points (GRPs): 100 GRPs means the average viewer sees your ad once. Prices per point reflect each market's size and rise sharply toward November; candidates get the lowest unit rate in the final weeks. Ads can be positive, contrast or negative - negative is strongest but drives backlash if you are always attacking. Markets cross state lines: Philadelphia ads also reach New Jersey and Delaware.",
                        "Digital ads are bought by state, then split across six platforms with a message for each (Money tab). Facebook skews older, Instagram and YouTube younger, TikTok creators reach Gen Z, streaming TV reaches cord-cutters over 30, and search ads build lists and remind people to vote. Messages: biography (name recognition), your issue focus, attack, get out the vote (late), or fundraising.",
                        "Self-funding: set a weekly transfer from your own fortune and it arrives every week until you change it or the money runs out; you can also write a one-time check. Heavy personal spending costs you a little with progressive voters.",
                        "A strong debate or a podcast that lands brings a burst of small-dollar donations.",
                        "Headquarters can run lean, standard or as a juggernaut. Running out of cash forces debt and hurts morale.",
                        "Allied super PACs are run by the computer: in the general election you can 'red-box' a state to signal where you want them to spend."},
                // Field
                {"Field offices house paid organisers. Each organiser recruits and manages volunteers (roughly 45 active at a time); volunteers take shifts; shifts knock doors, make calls, send texts and reach friends and family. Contacts persuade a sliver of voters and - in the final six weeks - turn out your supporters.",
                        "Volunteer activity ramps up as each state's vote approaches: the last weekend is the busiest of the campaign. A strong battleground program reaches hundreds of thousands of doors a week by October and a million in the final week; a national campaign knocks tens of millions of doors.",
                        "Transfers to a state party fund its coordinated campaign: it hires organisers with a lag and also helps down-ballot candidates."},
                // Message, promises, debates
                {"Every week choose a frame (your message), an attack line (or stay positive) and an issue focus: one main issue and up to two secondary ones. Credibility on an issue (0-100) builds every week you stay on it - fastest on the main issue - and drives how far voters trust you to handle it. Frames that fit the moment and your strengths build a narrative over weeks; switching frames resets it. Attack lines stick to opponents who fit them - flip-floppers, candidates over 70, the undisciplined, the extreme - and backfire if you go negative too often.",
                        "Policy promises are permanent. Each moves your platform toward a position, wins over some voter groups and angers others, and may change how big donors feel about you. Promises far from your ideology cost you with activists (enthusiasm) and invite flip-flop attacks - but the general electorate may reward a move to the middle. Promises that contradict earlier ones are punished.",
                        "Debates: in the primaries, candidates qualify by polling. Questions are drawn from the issues voters care about most. For each one you first choose an approach - policy depth, attack, pivot to message, personal story, a prepared zinger, or defend your record - then the actual line you deliver (attacks name a rival). When a rival comes after you, you get a rebuttal moment. Approaches beat each other like rock-paper-scissors; your ratings and debate prep decide how well you execute. In the general you negotiate how many debates, when, and on which networks."},
                // Polls and map
                {"Internal polls cost money; bigger samples and better methods (live phone plus text) are more accurate. Every internal poll includes crosstabs for up to thirty voter groups (too-small groups are not reported). Public polls appear on their own each week, mostly in competitive states; they have house effects - and this cycle, like every cycle, the polls may share a systematic error.",
                        "#The map views",
                        "Spending: your recent spending per state (deeper green = deeper investment).  Internal polls: your own polling - blue and red by who leads, faint when the poll is old or small, hatched where you have none.  Public polls: the press's averages.  Volunteers & doors: shifts this week (colour) and doors knocked to date (labels).  Primary calendar: results and upcoming dates.  Ballot access (independents): where you qualify.",
                        "#Your pollster and the issues",
                        "Set your pollster to light, standard or heavy (Polls tab) and polls arrive on their own. The Issues view shows the most important problem by state and group, whom voters trust to handle each issue, how many agree with your positions, and how those numbers have moved."},
                // Primaries
                {"Democrats award delegates proportionally to candidates above 15% in each state (statewide and by district); this game models the statewide split. Their 2028 early window, adopted by the DNC in August 2026, runs South Carolina (Jan 22), Nevada, New Hampshire, New Mexico, Michigan and Virginia (Feb 29); you can change it before you start. Republicans begin in Iowa, New Hampshire, Nevada and South Carolina; after mid-March many Republican contests are winner-take-all or winner-take-most.",
                        "Two years out, primary polls mostly measure name recognition; as voters tune in, skill, money and organisation take over. Winning - and beating expectations - creates momentum: money, press and voters follow. Candidates with no money and no path drop out: most of their voters move to the rival closest to them politically. If you're still in the race you can bid for the departing candidate's endorsement with policy promises, a cabinet post, the vice-presidential slot or a convention speech - and your rivals bid too. A majority of all delegates clinches the nomination; if no one has one by the convention, it goes to a second ballot.",
                        "The sitting president is term-limited and is never a candidate, but his endorsement in the Republican primary is the most valuable prize in politics."},
                // Independent
                {"You can run as an independent with a candidate you create, in any scenario (it's easiest when a third force is already stirring). Start on Labor Day already on every ballot, or start the long campaign when the primaries begin and earn your ballot lines state by state.",
                        "#Ballot access",
                        "Each state sets its own signature requirement and filing deadline (2024 rules): from a few hundred signatures to well over a hundred thousand, due anywhere from spring to late summer. About three in four raw signatures survive challenges; the major parties sue to strike more. Paid circulators cost about $13 a signature, volunteers and organisers gather them for free. Miss a deadline and you can only be a write-in there.",
                        "#Getting heard",
                        "Independents start unknown and without a party's donors. Name recognition, viral moments and podcasts matter even more. Reach 15% in the national polls to make the fall debates.",
                        "#Winning",
                        "270 electoral votes wins outright. If no one reaches 270, the new House picks the president, one vote per state delegation - and the game estimates the House results to decide it."},
                // Electorate model
                {"Every state's electorate is modelled as 96 cells: 16 religious, racial and educational blocs (white evangelicals with and without degrees, white Catholics, other whites by education, Jewish, Latter-day Saint, Black, Mexican-American and other Hispanic, Cuban and Venezuelan, Puerto Rican and Dominican, Asian American, Native American, Arab and Muslim American, multiracial) times three age groups times two sexes. Overlays add rural/suburban/urban, union, veteran, gun-owning, LGBTQ, student and parent shares.",
                        "Each cell's 2024 lean is calibrated so the model reproduces every state's 2024 result and the national exit-poll and validated-voter estimates for each group; 2020, 2022 and 2026 data shape trends and turnout. Composition is projected to 2028 (an older, more diverse, more college-educated electorate).",
                        "Voters weigh 18 issues with group-specific views and salience: young voters care more about housing, student debt and Gaza; seniors about Social Security and Medicare; Arab and Muslim Americans about Israel above almost anything; evangelicals about abortion and gender; union households about trade and jobs. Candidates' ratings, records, platforms and group appeal, the national environment (approval, the economy) and every campaign's ads, visits and field work move each cell.",
                        "Ratings are the game's estimates from public records - election results, fundraising, debate and interview reviews - not facts. Nothing in the game quotes a real person, and every event in a game is fiction."},
                // Sources
                {"Election results: county and state returns for 2016, 2020 and 2024 (tonmcg/US_County_Level_Election_Results; MIT Election Data and Science Lab). 2022 House popular vote by state (Cook Political Report).",
                        "Demographics: American Community Survey county data (via MIT Election Lab's election-context file); religion (PRRI American Values Atlas); union households (BLS Union Members 2024); veterans (VA); gun ownership (RAND state estimates); LGBTQ identification (Gallup, Williams Institute).",
                        "How groups voted in 2024: Pew Research Center validated-voter study; AP VoteCast; national exit polls.",
                        "Map: us-atlas (Census Bureau cartographic boundaries, Albers USA projection).",
                        "Ballot access: signature requirements from the National Association of Secretaries of State (2024) and 2024 independent filing deadlines. Social media audiences by age: Pew Research Center (2024). Podcast audiences: the game's rough estimates from public view counts. Most important problem: modelled on Gallup's monthly question.",
                        "TV markets: Nielsen DMA household estimates 2024-25; cost per point and political ad pricing patterns from AdImpact's 2024 cycle reporting. Digital CPMs: industry averages.",
                        "2026 polling: Silver Bulletin generic-ballot average (D+8.7) and presidential approval (37.4%) as of October 28, 2026; 2028 primary polling from public national surveys (Emerson, Echelon Insights, YouGov, Atlas Intel and others).",
                        "Calendar and rules: the 2028 Democratic early-state calendar adopted by the DNC on August 15, 2026; RNC 2028 convention in Houston; DNC and RNC delegate-selection rules.",
                        "Money and field: FEC filings for the 2024 presidential committees; published 2024 field-office counts; Green and Gerber, Get Out the Vote; Kalla and Broockman (2018) on persuasion.",
                        "All numbers are rounded approximations for play. The game is not affiliated with any campaign, party or person."}};
    }

    // =====================================================================
    //  31.  DECISION SCREENS: endorsement bids, dropouts, the closing argument
    // =====================================================================
    static final class Deals {
        static boolean[] pick;
        static Map<Integer, Endorse.Offer> rivals;

        static String pct(double v) { return Math.round(v * 100) + "%"; }

        /** Before-and-after national primary poll, top candidates by projected share. */
        static double shiftTable(Battleground2028 u, Graphics2D g, Game game, Camp gone, double[] before, double[] after, double x, double y, double w) {
            label(g, "Projected national primary poll", x, y + 10, C_GOLD);
            y += 18;
            List<Camp> cs = new ArrayList<Camp>();
            for (Camp k : game.camps) if (k.party == gone.party && (k.active || k == gone) && k.idx < before.length) cs.add(k);
            final double[] a = after;
            Collections.sort(cs, (p, q) -> Double.compare(a[q.idx] + (q == gone ? -1 : 0), a[p.idx] + (p == gone ? -1 : 0)));
            Camp me = game.me();
            int shown = 0;
            for (Camp k : cs) {
                if (shown >= 8) break;
                double b = before[k.idx], af = k == gone ? 0 : after[k.idx];
                if (b < 0.005 && af < 0.005) continue;
                Portrait.avatar(g, k.c, x + 9, y + 8, 8, k == me ? C_GOLD : null);
                text(g, k.last(), x + 22, y + 12, k == me ? F_XSB : F_XS, k == gone ? C_FAINT : k == me ? C_GOLD : C_TEXT);
                text(g, pct(b), x + w - 120, y + 12, F_XS, C_DIM);
                text(g, "->", x + w - 90, y + 12, F_XS, C_FAINT);
                textR(g, k == gone ? "out" : pct(af), x + w - 40, y + 12, F_XSB, k == gone ? C_FAINT : C_TEXT);
                double d = (af - b) * 100;
                if (k != gone && Math.abs(d) >= 0.5) textR(g, (d > 0 ? "+" : "") + Math.round(d), x + w, y + 12, F_XSB, d > 0 ? C_GOOD : C_BAD);
                y += 19;
                shown++;
            }
            return y;
        }

        static Endorse.Offer offer(List<Integer> asks) {
            Endorse.Offer o = new Endorse.Offer();
            for (int i = 0; i < asks.size(); i++) if (pick[i]) o.promises.add(asks.get(i));
            int n = asks.size();
            o.cabinet = pick[n];
            o.vp = pick[n + 1];
            o.speaking = pick[n + 2];
            return o;
        }

        /** A rival quits: the poll shift, then the bidding for the endorsement. */
        static void endorse(Battleground2028 u, Game game, Object[] p) {
            Camp me = game.me();
            Camp from = game.camp((Integer) p[4]);
            double[] before = (double[]) p[5], after = (double[]) p[6];
            final List<Integer> asks = Endorse.asks(game, from, me, 3);
            pick = new boolean[asks.size() + 3];
            rivals = Endorse.rivalOffers(game, from);
            final List<Camp> bidders = Endorse.bidders(game, from);
            Modal m = new Modal();
            m.title = from.name() + " drops out";
            m.face = from.c;
            m.col = C_GOLD;
            m.w = 940;
            String his = from.c.look.female ? "her" : "his";
            m.body = from.last() + "'s supporters - about " + pct(before[from.idx]) + " of " + PARTY[from.party] + " primary voters - are up for grabs. Most will drift to candidates who share "
                    + his + " politics (" + FACTIONS[from.c.faction].name + "). " + from.last() + " will endorse whoever is closest - unless someone makes a better offer.";
            m.extraH = 330;
            m.painter = (g, x, y, w, h) -> {
                double lw = 300;
                shiftTable(u, g, game, from, before, after, x, y + 2, lw);
                // the offer
                double ox = x + lw + 30, ow = w - lw - 30;
                label(g, "Your offer for the endorsement", ox, y + 12, C_GOLD);
                double oy = y + 22;
                for (int i = 0; i < pick.length; i++) {
                    final int k = i;
                    String name;
                    String tip;
                    boolean off = false;
                    boolean enabled = true;
                    if (i < asks.size()) {
                        Promise pr = PROMISES[asks.get(i)];
                        name = "Promise: " + pr.name;
                        tip = Campaign.preview(game, me, asks.get(i));
                        off = Campaign.offIdeology(me, asks.get(i)) > 0.6;
                    } else if (i == asks.size()) { name = "A cabinet post in your administration"; tip = "Worth a lot to a former rival; costs you little now."; }
                    else if (i == asks.size() + 1) {
                        name = "Serious consideration for vice president";
                        enabled = me.vpPromised == null;
                        tip = enabled ? "A powerful lure. If you later choose someone else, their supporters will feel betrayed." : "You have already promised the vice-presidential slot to someone else.";
                    } else { name = "A prime-time convention speech"; tip = "A moderate bump: a unity moment at your convention."; }
                    if (!enabled) pick[k] = false;
                    u.toggle(g, ox, oy, pick[k], enabled ? () -> pick[k] = !pick[k] : null, tip);
                    textFit(g, name, ox + 44, oy + 15, ow - 140, F_XS, enabled ? C_TEXT : C_FAINT);
                    if (off) chip(g, "OFF-IDEOLOGY", ox + ow - 92, oy + 2, C_AMBER, false);
                    oy += 26;
                }
                if (asks.isEmpty()) { text(g, "You already stand where " + from.last() + " does on the big asks.", ox, oy + 12, F_XS, C_DIM); oy += 20; }
                // the odds
                Endorse.Offer mine = offer(asks);
                Endorse.Offer[] offs = new Endorse.Offer[bidders.size()];
                for (int i = 0; i < bidders.size(); i++) offs[i] = bidders.get(i).human ? mine : rivals.get(bidders.get(i).idx);
                double[] odds = Endorse.odds(game, from, bidders, offs);
                oy += 10;
                int mi = bidders.indexOf(me);
                text(g, "Chance " + from.last() + " endorses you: " + (mi >= 0 ? pct(odds[mi]) : "-"), ox, oy + 14, F_H3, C_GOLD);
                oy += 24;
                StringBuilder b = new StringBuilder();
                List<Integer> order = new ArrayList<Integer>();
                for (int i = 0; i < bidders.size(); i++) if (i != mi) order.add(i);
                final double[] oo = odds;
                Collections.sort(order, (pp, qq) -> Double.compare(oo[qq], oo[pp]));
                for (int i = 0; i < Math.min(3, order.size()); i++) b.append(i > 0 ? ",  " : "").append(bidders.get(order.get(i)).last()).append(" ").append(pct(odds[order.get(i)]));
                b.append(",  nobody ").append(pct(odds[odds.length - 1]));
                textFit(g, b.toString(), ox, oy + 12, ow, F_XS, C_DIM);
                oy += 18;
                int rivalDeals = 0;
                for (Endorse.Offer o : rivals.values()) if (o != null && !o.empty()) rivalDeals++;
                if (rivalDeals > 0) wrap(g, rivalDeals + " rival campaign" + (rivalDeals > 1 ? "s are" : " is") + " also making offers.", ox, oy + 12, ow, F_T, C_FAINT);
            };
            m.buttons = new String[]{"Don't bid", "Make the offer"};
            m.actions = new Runnable[]{() -> { Camp to = Endorse.resolve(game, from, null, rivals); after(u, game, from, to); },
                    () -> { Camp to = Endorse.resolve(game, from, offer(asks), rivals); after(u, game, from, to); }};
            u.modal = m;
            snd(Sfx.ALERT);
        }

        static void after(Battleground2028 u, Game game, Camp from, Camp to) {
            Camp me = game.me();
            if (to == me) { u.toast(from.last() + " endorses you!", C_GOOD); snd(Sfx.CHEER, 0.5, 1); }
            else if (to != null) u.toast(from.last() + " endorses " + to.last() + ".", C_AMBER);
            else u.toast(from.last() + " stays neutral.", C_DIM);
            game.dirty = true;
            Hq.showPending(u);
        }

        /** A rival quits and the endorsement is already settled (you were not in a position to bid). */
        static void dropout(Battleground2028 u, Game game, Object[] p) {
            Camp from = game.camp((Integer) p[4]);
            double[] before = (double[]) p[5], after = (double[]) p[6];
            String toS = (String) p[2];
            Camp to = toS == null || toS.isEmpty() ? null : game.camp(Integer.parseInt(toS));
            Modal m = new Modal();
            m.title = from.name() + " drops out";
            m.face = from.c;
            m.w = 700;
            m.body = (to != null ? from.last() + " endorses " + to.name() + "." : from.last() + " does not endorse anyone.") + " Here is where the polls are expected to go.";
            m.extraH = 190;
            m.painter = (g, x, y, w, h) -> shiftTable(u, g, game, from, before, after, x, y + 2, Math.min(360, w));
            m.buttons = new String[]{"Continue"};
            m.actions = new Runnable[]{() -> Hq.showPending(u)};
            u.modal = m;
        }

        /** The closing argument for the final two weeks. */
        static void closing(Battleground2028 u, Game game) {
            Camp me = game.me();
            Modal m = new Modal();
            m.title = me.closing < 0 ? "Your closing argument" : "Change your closing argument?";
            m.body = me.closing < 0
                    ? "Two weeks to go, and from now on each turn is a single day. What is the last thing you want voters to hear? A closing argument builds every day you stick with it; changing it later costs you."
                    : "You are closing on \"" + CLOSING[me.closing] + "\". Switching now throws away most of what it has built and reporters will call it a sign of nerves.";
            m.col = C_GOLD;
            m.stacked = true;
            m.face = me.c;
            m.w = 820;
            int n = CLOSING.length + (me.closing >= 0 ? 1 : 0);
            m.buttons = new String[n];
            m.descs = new String[n];
            m.actions = new Runnable[n];
            for (int i = 0; i < CLOSING.length; i++) {
                final int k = i;
                double fit = Closing.fit(game, me, i);
                m.buttons[i] = CLOSING[i] + (i == me.closing ? "  (current)" : "");
                m.descs[i] = CLOSING_DESC[i] + "  Fit for you: " + Math.round(fit * 66) + "/100.";
                m.actions[i] = () -> { Closing.choose(game, me, k); Hq.showPending(u); };
            }
            if (me.closing >= 0) { m.buttons[n - 1] = "Keep the current argument"; m.descs[n - 1] = "Stay the course."; m.actions[n - 1] = () -> Hq.showPending(u); }
            u.modal = m;
            snd(Sfx.ALERT);
        }

        /** When you quit the race yourself: whom do you endorse? */
        static void suspend(Battleground2028 u, Game game) {
            Camp me = game.me();
            List<Camp> rest = new ArrayList<Camp>();
            for (Camp k : game.camps) if (k != me && k.active && k.party == me.party) rest.add(k);
            final double[] pp = game.primaryPoll;
            if (pp != null) Collections.sort(rest, (a, b) -> Double.compare(pp[b.idx], pp[a.idx]));
            String[] items = new String[Math.min(6, rest.size()) + 1];
            for (int i = 0; i < items.length - 1; i++) items[i] = "Endorse " + rest.get(i).name();
            items[items.length - 1] = "Leave without endorsing";
            u.openPopup(u.mx - 120, u.my - 200, 280, "Suspend the campaign", items, i -> {
                double share = pp != null ? pp[me.idx] : 0.02;
                me.active = false;
                me.dropWeek = game.week;
                game.log(NK_BIG, me.name() + " suspends " + (me.c.look.female ? "her" : "his") + " campaign.");
                Endorse.redistribute(game, me, share);
                if (i < items.length - 1) Endorse.apply(game, me, rest.get(i), null);
                Hq.lostRace(u);
            });
        }
    }

    // =====================================================================
    //  32.  WHO'S RUNNING (the field), THE INDEPENDENT'S START, AND THE LOADING SCREEN
    // =====================================================================
    static final class FieldScreen {
        static Cand cand;                    // the player's candidate, waiting to start
        static boolean opponentsOnly;        // independent from Labor Day: just pick the two nominees

        static void open(Battleground2028 u, Cand c, boolean oppOnly) {
            cand = c;
            opponentsOnly = oppOnly;
            Settings s = u.setup;
            if (s.lockOf(c.party) != null) { if (c.party == DEM) s.lockD = null; else if (c.party == REP) s.lockR = null; }
            s.sitOut.remove(c.id);
            if (oppOnly) {
                if (s.lockD == null) s.lockD = Setup.likelyNominee(DEM).id;
                if (s.lockR == null) s.lockR = Setup.likelyNominee(REP).id;
            }
            u.go(SC_FIELD);
        }

        static void draw(Battleground2028 u, Graphics2D g) {
            Settings s = u.setup;
            Screens.header(u, g, opponentsOnly ? "Your Opponents" : "Who's Running",
                    opponentsOnly ? "It's Labor Day 2028. Choose the Democratic and Republican nominees you'll face - or take the likeliest."
                            : "Shape the field: switch candidates in or out of each primary, and if you like, decide who wins the other party's nomination.");
            for (int p = DEM; p <= REP; p++) {
                double x = p == DEM ? 48 : 812, w = 740, y = 118, h = 690;
                u.panel(g, x, y, w, h, null);
                text(g, opponentsOnly ? (p == DEM ? "The Democratic nominee" : "The Republican nominee") : p == DEM ? "Democratic primary" : "Republican primary", x + 18, y + 30, F_H2, partyLight(p));
                List<Cand> list = Screens.partyList(p);
                int in = 0;
                for (Cand c : list) if (!s.sitOut.contains(c.id) || c == cand) in++;
                if (!opponentsOnly) textR(g, in + " of " + list.size() + " running", x + w - 18, y + 30, F_XS, C_DIM);
                double cw = 176, ch = 70;
                int cols = 4;
                if (opponentsOnly) {
                    final int party = p;
                    for (int i = 0; i < list.size(); i++) {
                        final Cand c = list.get(i);
                        double cx = x + 18 + (i % cols) * (cw + 4), cy = y + 46 + (i / cols) * (ch + 4);
                        boolean pick = c.id.equals(s.lockOf(p));
                        u.card(g, cx, cy, cw, ch, pick, () -> { if (party == DEM) s.lockD = c.id; else s.lockR = c.id; }, "Face " + c.name() + " in the general election.");
                        Portrait.draw(g, c, cx + 5, cy + 5, 50, 60, 0, 0, true, true);
                        textFit(g, c.name(), cx + 60, cy + 22, cw - 64, F_XSB, C_TEXT);
                        text(g, "Polling " + Math.round(c.poll) + "%", cx + 60, cy + 38, F_T, C_DIM);
                        if (pick) chip(g, "NOMINEE", cx + 60, cy + 46, C_GOOD, true);
                    }
                } else {
                    for (int i = 0; i < list.size(); i++) {
                        final Cand c = list.get(i);
                        double cx = x + 18 + (i % cols) * (cw + 4), cy = y + 46 + (i / cols) * (ch + 4);
                        boolean mine = c == cand || c.id.equals(cand.id);
                        boolean locked = c.id.equals(s.lockOf(p));
                        boolean running = mine || locked || !s.sitOut.contains(c.id);
                        u.card(g, cx, cy, cw, ch, running && (mine || locked), mine || locked ? null : () -> { if (s.sitOut.contains(c.id)) s.sitOut.remove(c.id); else s.sitOut.add(c.id); },
                                mine ? "That's you." : locked ? "Locked in as the nominee." : running ? "Click to keep " + c.name() + " out of the race." : "Click to put " + c.name() + " back in the race.");
                        Composite oc = g.getComposite();
                        if (!running) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
                        Portrait.draw(g, c, cx + 5, cy + 5, 50, 60, 0, 0, true, true);
                        textFit(g, c.name(), cx + 60, cy + 22, cw - 64, F_XSB, C_TEXT);
                        text(g, "Polling " + Math.round(c.poll) + "%", cx + 60, cy + 38, F_T, C_DIM);
                        g.setComposite(oc);
                        String tag = mine ? "YOU" : locked ? "NOMINEE" : running ? "RUNNING" : "SITTING OUT";
                        chip(g, tag, cx + 60, cy + 46, mine ? C_GOLD : locked ? C_GOOD : running ? partyColor(p) : C_FAINT, mine || locked);
                    }
                }
                if (opponentsOnly) continue;
                // nominee lock
                double ly = y + h - 96;
                boolean ownParty = cand.party == p;
                label(g, ownParty ? "Your party's nominee" : "The nominee", x + 18, ly, C_GOLD);
                if (ownParty) wrap(g, "You'll have to win this one yourself.", x + 18, ly + 20, w - 36, F_S, C_DIM);
                else {
                    String cur = s.lockOf(p);
                    Cand lc = cur != null ? candById(cur) : null;
                    if (opponentsOnly && lc == null) lc = Setup.likelyNominee(p);
                    final int party = p;
                    u.button(g, x + 18, ly + 10, 320, 34, lc != null ? lc.name() : "Let the primaries decide", B_NORM, () -> {
                        List<Cand> l = Screens.partyList(party);
                        String[] items = new String[l.size() + (opponentsOnly ? 0 : 1)];
                        int k0 = 0;
                        if (!opponentsOnly) items[k0++] = "Let the primaries decide";
                        for (Cand c : l) items[k0++] = c.name();
                        Popup pp = u.openPopup(u.mx, 140, 260, "Nominee", items, i -> {
                            int j = opponentsOnly ? i : i - 1;
                            String id = j < 0 ? null : l.get(j).id;
                            if (party == DEM) s.lockD = id; else s.lockR = id;
                            if (id != null) s.sitOut.remove(id);
                        });
                        pp.y = 120;
                    }, "Lock in who wins this party's nomination. They will win every contest; the rest of the race plays out around them.");
                    if (lc != null) {
                        Portrait.draw(g, lc, x + w - 18 - 100, ly - 40, 100, 120, u.tsec, 0, false, true);
                        if (!opponentsOnly) wrap(g, lc.name() + " will win every " + (p == DEM ? "Democratic" : "Republican") + " contest. The rest of the race plays out around them.", x + 350, ly + 22, w - 480, F_XS, C_DIM);
                    } else wrap(g, "The primaries decide - anyone in the field can win.", x + 350, ly + 22, w - 480, F_XS, C_DIM);
                }
            }
            u.button(g, 48, 832, 150, 44, "Back", B_GHOST, () -> u.go(cand.custom ? SC_CREATE : SC_PICK));
            if (opponentsOnly) u.button(g, 210, 832, 220, 44, "Likeliest nominees", B_NORM, () -> { s.lockD = Setup.likelyNominee(DEM).id; s.lockR = Setup.likelyNominee(REP).id; });
            if (!opponentsOnly) u.button(g, 210, 832, 200, 44, "Everyone runs", B_NORM, () -> { s.sitOut.clear(); s.lockD = null; s.lockR = null; });
            u.button(g, DW - 48 - 300, 832, 300, 44, "Start the campaign", B_GOLD, () -> Screens.startGame(u, cand));
        }
    }

    /** Runs the parties' invisible primary in the background before an independent's long campaign. */
    static final class Loading {
        static volatile Game result;
        static volatile boolean busy;
        static volatile String note = "";
        static int target = 61;

        static void start(Battleground2028 u, Settings s) {
            busy = true;
            result = null;
            note = "Setting up the race...";
            u.go(SC_LOADING);
            new Thread(new Runnable() {
                public void run() {
                    try {
                        Game g = Setup.newGame(s);
                        target = Math.min(Primary.firstContest(g, DEM), Primary.firstContest(g, REP)) - 1;
                        note = "The parties' invisible primary is under way...";
                        LoadingGame.current = g;
                        Setup.prestart(g);
                        result = g;
                    } catch (Throwable t) {
                        t.printStackTrace();
                        note = "Could not set up the race: " + t;
                    }
                    busy = false;
                }
            }, "bg-prestart").start();
        }

        static void update(Battleground2028 u) {
            if (!busy && result != null) {
                Game g = result;
                result = null;
                LoadingGame.current = null;
                Screens.finishStart(u, g);
            }
        }

        static void draw(Battleground2028 u, Graphics2D g) {
            u.backdrop(g);
            double cx = DW / 2, cy = DH / 2 - 40;
            textC(g, "An Independent Run", cx, cy - 90, F_DISPLAY, Color.white);
            textC(g, note, cx, cy - 50, F_B, C_DIM);
            Game lg = LoadingGame.current;
            double f = lg == null ? 0 : clamp(lg.week / (double) Math.max(1, target), 0, 1);
            bar(g, cx - 260, cy - 20, 520, 10, f, C_IND);
            if (lg != null) textC(g, weekStr(lg.week), cx, cy + 14, F_XS, C_DIM);
            g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(C_IND);
            double st = u.tsec * 360 % 360;
            g.draw(new Arc2D.Double(cx - 22, cy + 40, 44, 44, -st, 260, Arc2D.OPEN));
            g.setStroke(new BasicStroke(1f));
            wrap(g, "While the parties fight it out through 2027, you build your plan. You enter the race in January 2028, when the primaries begin - with no party machine, no ballot lines and everything to prove.",
                    cx - 300, cy + 130, 600, F_S, C_DIM);
        }
    }

    static final class LoadingGame { static volatile Game current; }

    // =====================================================================
    //  33.  THE RESULTS EXPLORER  -  states, groups, turnout and Congress
    // =====================================================================
    static final class Results {
        static int tab = 0, mapView = 0, sel = -1, sortBy = 0, grpState = -1;
        static Game cacheFor;
        static double[] m28, m24, swing, turn28, turn24, ev;
        static double[] g24, g28, gs24, gs28, gt24, gt28;     // national group margins, shares of voters, turnout rates
        static double[][] stateGroup24, stateGroup28;
        static double[] gotvVotes;                            // votes the player's field program added, by state
        static double tot24, tot28, elig28;
        static final String[] TABS = {"Map", "States", "Groups", "Turnout", "Congress"};
        static final String[] MAP_VIEWS = {"Result", "Swing since 2024", "Turnout change"};
        static final int[] KEY_GROUPS = {0, 1, 2, 3, 4, 5, 8, 9, 10, 12, 16, 21, 22, 24, 28};

        static void open(Battleground2028 u) {
            tab = 0; mapView = 0; sel = -1; grpState = -1;
            u.go(SC_RESULTS);
        }

        static double margin3(double[] row) { double t = row[0] + row[1] + row[2]; return t <= 0 ? 0 : (row[0] - row[1]) / t * 100; }

        static int winner(Game g, int s) { double[] r = g.result[s]; return Vote.winner(r[0], r[1], r.length > 3 ? r[3] : 0); }

        /** Cell turnout and D-vs-R preference at the end of the campaign. */
        static double[] cell(Game g, Camp d, Camp r, Camp ind, int s, int c, double indNat) {
            double p = logistic(Vote.cellLogit(g, d, r, s, c));
            double indSh = 0;
            if (ind != null && ind.party == IND) {
                double dD = d != null ? Vote.distance(g, d.plat, s, c) : 0.4, dR = r != null ? Vote.distance(g, r.plat, s, c) : 0.4;
                indSh = Vote.indShare(g, ind, d, r, s, c, dD, dR, indNat);
            }
            return new double[]{p, Vote.turnout(g, d, r, ind, s, c, p, indSh), indSh};
        }

        static void prepare(Game g) {
            if (cacheFor == g) return;
            cacheFor = g;
            m28 = new double[NS]; m24 = new double[NS]; swing = new double[NS]; turn28 = new double[NS]; turn24 = new double[NS]; ev = new double[NS];
            tot24 = 0; tot28 = 0; elig28 = 0;
            for (int s = 0; s < NS; s++) {
                StateDef d = STATES[s];
                m28[s] = margin3(g.result[s]);
                m24[s] = (d.d24 - d.r24) / d.t24 * 100;
                swing[s] = m28[s] - m24[s];
                turn28[s] = g.result[s][0] + g.result[s][1] + g.result[s][2];
                turn24[s] = d.t24;
                tot24 += turn24[s]; tot28 += turn28[s];
                for (int c = 0; c < NCELL; c++) elig28 += Elect.cvap[s][c];
            }
            Camp d = g.nominee(DEM), r = g.nominee(REP), ind = g.indep();
            double indNat = ind != null ? g.thirdPoll(ind) : 0;
            Camp me = g.me();
            g24 = new double[NG]; g28 = new double[NG]; gs24 = new double[NG]; gs28 = new double[NG]; gt24 = new double[NG]; gt28 = new double[NG];
            stateGroup24 = new double[NS][]; stateGroup28 = new double[NS][];
            gotvVotes = new double[NS];
            double[] dv24 = new double[NG], v24 = new double[NG], dv28 = new double[NG], rv28 = new double[NG], v28 = new double[NG], el24 = new double[NG], el28 = new double[NG];
            double all24 = 0, all28 = 0;
            for (int s = 0; s < NS; s++) {
                double[] sd24 = new double[NG], sv24 = new double[NG], sd28 = new double[NG], sr28 = new double[NG];
                for (int c = 0; c < NCELL; c++) {
                    double n24 = Elect.cvap24[s][c], n28 = Elect.cvap[s][c];
                    double t24 = Elect.turnB[s][c];
                    double p24 = logistic(Elect.baseL[s][c]);
                    double[] x = cell(g, d, r, ind, s, c, indNat);
                    double p = x[0], t = x[1], is = x[2];
                    all24 += n24 * t24; all28 += n28 * t;
                    if (me != null && me.gotv[s] > 0 && (me.party <= REP)) {
                        double pm = me.party == DEM ? p : 1 - p;
                        gotvVotes[s] += n28 * Week.gotvSat(me.gotv[s]) * pm * (1 - t24) * pm;
                    }
                    for (int gr = 0; gr < NG; gr++) {
                        double w = Vote.member(gr, s, c);
                        if (w <= 0) continue;
                        int o = GROUP_OV[gr];
                        double adj = o >= 0 ? OV_LEAN[o] * (1 - Elect.ov[s][c][o]) : 0;
                        double q24 = o >= 0 ? logistic(Elect.baseL[s][c] + adj) : p24;
                        double q28 = o >= 0 ? logistic(Vote.cellLogit(g, d, r, s, c) + adj) : p;
                        double a = n24 * t24 * w, b = n28 * t * w * (1 - is);
                        sd24[gr] += a * q24; sv24[gr] += a;
                        sd28[gr] += b * q28; sr28[gr] += b * (1 - q28);
                        dv24[gr] += a * q24; v24[gr] += a;
                        dv28[gr] += b * q28; rv28[gr] += b * (1 - q28); v28[gr] += n28 * t * w;
                        el24[gr] += n24 * w; el28[gr] += n28 * w;
                    }
                }
                stateGroup24[s] = new double[NG]; stateGroup28[s] = new double[NG];
                for (int gr = 0; gr < NG; gr++) {
                    stateGroup24[s][gr] = sv24[gr] > 0 ? (2 * sd24[gr] / sv24[gr] - 1) * 100 : Double.NaN;
                    stateGroup28[s][gr] = sd28[gr] + sr28[gr] > 0 ? (sd28[gr] - sr28[gr]) / (sd28[gr] + sr28[gr]) * 100 : Double.NaN;
                    if (sv24[gr] < 20000) { stateGroup24[s][gr] = Double.NaN; stateGroup28[s][gr] = Double.NaN; }
                }
            }
            for (int gr = 0; gr < NG; gr++) {
                g24[gr] = (2 * dv24[gr] / Math.max(1, v24[gr]) - 1) * 100;
                g28[gr] = (dv28[gr] - rv28[gr]) / Math.max(1, dv28[gr] + rv28[gr]) * 100;
                gs24[gr] = v24[gr] / Math.max(1, all24);
                gs28[gr] = v28[gr] / Math.max(1, all28);
                gt24[gr] = v24[gr] / Math.max(1, el24[gr]);
                gt28[gr] = v28[gr] / Math.max(1, el28[gr]);
            }
        }

        static Color resultColour(Game g, int s) {
            int w = winner(g, s);
            if (w == IND) return mix(C_IND, rgb(0x7a4a05), clamp((g.result[s][3] / Math.max(1, turn28[s]) - 0.3) * 3, 0, 1));
            return marginColor(m28[s], 1);
        }

        static void draw(Battleground2028 u, Graphics2D g) {
            Game game = u.game;
            if (game == null || game.result == null) { u.go(SC_HOME); return; }
            prepare(game);
            Screens.header(u, g, "The Results", resultLine(game));
            u.segmented(g, DW - 48 - 560, 40, 560, 38, TABS, tab, i -> { tab = i; u.resetScroll("res"); }, null);
            switch (tab) {
                case 0: mapTab(u, g, game); break;
                case 1: statesTab(u, g, game); break;
                case 2: groupsTab(u, g, game); break;
                case 3: turnoutTab(u, g, game); break;
                default: congressTab(u, g, game); break;
            }
            u.button(g, 48, 832, 220, 44, "Back", B_GHOST, () -> u.go(SC_END));
            u.button(g, DW - 48 - 260, 832, 260, 44, "Main menu", B_NORM, () -> u.go(SC_HOME));
        }

        static String resultLine(Game game) {
            Camp d = game.nominee(DEM), r = game.nominee(REP), i = game.indep();
            double tot = Math.max(1, game.popD + game.popR + game.popO);
            String s = (d != null ? d.last() : "D") + " " + game.evD + " (" + fmt1(game.popD / tot * 100) + "%)  -  " + (r != null ? r.last() : "R") + " " + game.evR + " (" + fmt1(game.popR / tot * 100) + "%)";
            if (i != null && i.party == IND) s += "  -  " + i.last() + " " + game.evI + " (" + fmt1(game.popI / tot * 100) + "%)";
            if (game.contingent >= 0) s += "  -  decided by the House";
            return s;
        }

        // ---------------------------------------------------------------- map
        static void mapTab(Battleground2028 u, Graphics2D g, Game game) {
            u.segmented(g, 48, 118, 520, 30, MAP_VIEWS, mapView, i -> mapView = i, null);
            double[] f = USMap.fit(48, 160, 980, 600);
            for (int s = 0; s < NS; s++) {
                Color c;
                if (mapView == 0) c = resultColour(game, s);
                else if (mapView == 1) c = marginColor(swing[s] * 2.2, 1);
                else { double ch = turn28[s] / Math.max(1, turn24[s]) - 1; c = ch >= 0 ? mix(rgb(0x1d3a2a), rgb(0x45d483), clamp(ch / 0.15, 0, 1)) : mix(rgb(0x3a1d1d), rgb(0xff5d5d), clamp(-ch / 0.15, 0, 1)); }
                USMap.cur[s] = c;
            }
            Night.fitDraw(u, g, f);
            int hov = USMap.stateAt(f, u.mx, u.my);
            for (int s = 0; s < NS; s++) {
                if (USMap.shape[s] == null || USMap.isCallout(s)) continue;
                double lx = f[1] + STATES[s].cx * f[0], ly = f[2] + STATES[s].cy * f[0];
                if (USMap.bounds[s].getWidth() * f[0] > 44) {
                    String lab = mapView == 0 ? margin(m28[s]) : mapView == 1 ? (swing[s] >= 0 ? "D" : "R") + "+" + fmt1(Math.abs(swing[s])) : signed1((turn28[s] / turn24[s] - 1) * 100) + "%";
                    textC(g, lab, lx, ly + 16, F_T, new Color(255, 255, 255, 200));
                }
            }
            if (sel >= 0 && USMap.shape[sel] != null) {
                AffineTransform at = g.getTransform();
                g.translate(f[1], f[2]); g.scale(f[0], f[0]);
                g.setColor(C_GOLD);
                g.setStroke(new BasicStroke((float) (2.4 / f[0])));
                g.draw(USMap.shape[sel]);
                g.setTransform(at);
                g.setStroke(new BasicStroke(1f));
            }
            u.hit(48, 160, 980, 600, () -> { int s = USMap.stateAt(f, u.mx, u.my); if (s >= 0) { sel = s; grpState = s; u.resetScroll("res"); } }, hov >= 0 ? STATES[hov].name + " - click for details" : null);
            String leg = mapView == 0 ? "Winner by margin (deeper = bigger win)" : mapView == 1 ? "Change in the D-minus-R margin since 2024 (blue = moved toward the Democrats)" : "Change in total votes cast since 2024";
            text(g, leg, 48, 790, F_XS, C_DIM);
            stateDetail(u, g, game, sel, 1050, 118, DW - 48 - 1050, 700);
        }

        static void stateDetail(Battleground2028 u, Graphics2D g, Game game, int s, double x, double y, double w, double h) {
            u.panel(g, x, y, w, h, null);
            double off = u.beginScroll(g, "res", x, y + 4, w, h - 8);
            int h0 = u.hits.size();
            double cy = y + 14 - off;
            double px = x + 16, pw = w - 32;
            if (s < 0) {
                text(g, "Click a state", px, cy + 22, F_H1, Color.white);
                cy += 40;
                cy += wrap(g, "See its vote, how it swung from 2024, who turned out and how each group voted.", px, cy, pw, F_S, C_DIM) + 14;
                double mn = 1e9, mx = -1e9;
                for (int t = 0; t < NS; t++) { mn = Math.min(mn, swing[t]); mx = Math.max(mx, swing[t]); }
                label(g, mx > 0 ? "Biggest swings toward the Democrats" : "Smallest swings toward the Republicans", px, cy + 10, C_GOLD);
                cy += 16;
                cy = topList(g, swing, true, px, cy, pw);
                label(g, mn < 0 ? "Biggest swings toward the Republicans" : "Smallest swings toward the Democrats", px, cy + 16, C_GOLD);
                cy += 22;
                cy = topList(g, swing, false, px, cy, pw);
            } else {
                StateDef d = STATES[s];
                text(g, d.name, px, cy + 24, F_H1, Color.white);
                chip(g, d.ev + " EV", px + strW(g, d.name, F_H1) + 10, cy + 10, C_GOLD, false);
                cy += 40;
                double[] row = game.result[s];
                double tot = row[0] + row[1] + row[2];
                Camp dd = game.nominee(DEM), rr = game.nominee(REP), ii = game.indep();
                String[] names = {dd != null ? dd.name() : "Democrat", rr != null ? rr.name() : "Republican"};
                double[] votes = {row[0], row[1]};
                for (int k = 0; k < 2; k++) {
                    Color col = k == 0 ? C_DEM_L : C_REP_L;
                    text(g, names[k], px, cy + 14, F_SB, col);
                    textR(g, thousands(Math.round(votes[k])) + "  " + fmt1(votes[k] / tot * 100) + "%", px + pw, cy + 14, F_SB, C_TEXT);
                    bar(g, px, cy + 20, pw, 6, votes[k] / tot, k == 0 ? C_DEM : C_REP);
                    cy += 32;
                }
                if (ii != null && ii.party == IND && row.length > 3) {
                    text(g, ii.name() + " (I)", px, cy + 14, F_SB, C_IND);
                    textR(g, thousands(Math.round(row[3])) + "  " + fmt1(row[3] / tot * 100) + "%", px + pw, cy + 14, F_SB, C_TEXT);
                    bar(g, px, cy + 20, pw, 6, row[3] / tot, C_IND);
                    cy += 32;
                }
                double other = row[2] - (row.length > 3 ? row[3] : 0);
                text(g, "Others", px, cy + 14, F_XS, C_DIM);
                textR(g, thousands(Math.round(other)) + "  " + fmt1(other / tot * 100) + "%", px + pw, cy + 14, F_XS, C_DIM);
                cy += 26;
                String[][] kv = {{"Margin 2028", margin(m28[s])}, {"Margin 2024", margin(m24[s])}, {"Swing", (swing[s] >= 0 ? "toward D by " : "toward R by ") + fmt1(Math.abs(swing[s])) + " pts"},
                        {"Votes cast 2028", thousands(Math.round(turn28[s]))}, {"Votes cast 2024", thousands(Math.round(turn24[s]))},
                        {"Turnout change", signed1((turn28[s] / turn24[s] - 1) * 100) + "%"}, {"Cast before Election Day", Math.round(100 * General.bankedFrac(game, s, game.result)) + "%"}};
                for (String[] r : kv) { text(g, r[0], px, cy + 14, F_XS, C_TEXT); textR(g, r[1], px + pw, cy + 14, F_XSB, C_TEXT); cy += 18; }
                cy += 8;
                for (SenateRace sr : game.senate) if (sr.state == s) {
                    label(g, "Senate", px, cy + 12, C_GOLD);
                    cy += 16;
                    cy += wrap(g, sr.incumbent + " (" + (sr.party == DEM ? "D" : "R") + (sr.open ? ", open seat" : "") + "): " + (sr.margin > 0 ? "Democrats win" : "Republicans win") + " by " + fmt1(Math.abs(sr.margin)) + (((sr.margin > 0) != (sr.party == DEM)) ? " - a FLIP" : ""), px, cy + 12, pw, F_XS, C_TEXT) + 8;
                }
                Camp me = game.me();
                if (me != null) {
                    label(g, "Your campaign here", px, cy + 12, C_GOLD);
                    cy += 16;
                    String[][] mine = {{"Spent", money(me.spendState[s])}, {"Doors knocked", compact(me.doorsT[s])}, {"Rallies", String.valueOf(rallies(me, s))},
                            {"Votes your turnout program added (est.)", compact(gotvVotes[s])}};
                    for (String[] r : mine) { text(g, r[0], px, cy + 14, F_XS, C_TEXT); textR(g, r[1], px + pw, cy + 14, F_XSB, C_TEXT); cy += 18; }
                    cy += 8;
                }
                label(g, "How groups voted here (D minus R)", px, cy + 12, C_GOLD);
                text(g, "2024", px + pw - 110, cy + 12, F_TB, C_FAINT);
                text(g, "2028", px + pw - 50, cy + 12, F_TB, C_FAINT);
                cy += 16;
                for (int gr : KEY_GROUPS) {
                    double a = stateGroup24[s][gr], b = stateGroup28[s][gr];
                    if (Double.isNaN(a) || Double.isNaN(b)) continue;
                    textFit(g, GROUP[gr], px, cy + 13, pw - 130, F_XS, C_TEXT);
                    textR(g, margin(a), px + pw - 66, cy + 13, F_XS, a >= 0 ? C_DEM_L : C_REP_L);
                    textR(g, margin(b), px + pw, cy + 13, F_XSB, b >= 0 ? C_DEM_L : C_REP_L);
                    cy += 17;
                }
            }
            u.clipHits(h0, y + 4, y + h - 4);
            u.endScroll(g, "res", cy + off - y);
        }

        static int rallies(Camp k, int s) { int n = 0; for (Rally r : k.rallies) if (r.state == s) n++; return n; }

        static double topList(Graphics2D g, double[] v, boolean dir, double x, double y, double w) {
            Integer[] idx = new Integer[NS];
            for (int s = 0; s < NS; s++) idx[s] = s;
            final double[] vv = v;
            Arrays.sort(idx, (a, b) -> dir ? Double.compare(vv[b], vv[a]) : Double.compare(vv[a], vv[b]));
            for (int i = 0; i < 6; i++) {
                int s = idx[i];
                text(g, STATES[s].name, x, y + 14, F_XS, C_TEXT);
                textR(g, (v[s] >= 0 ? "D+" : "R+") + fmt1(Math.abs(v[s])), x + w, y + 14, F_XSB, v[s] >= 0 ? C_DEM_L : C_REP_L);
                y += 17;
            }
            return y;
        }

        // ---------------------------------------------------------------- states table
        static void statesTab(Battleground2028 u, Graphics2D g, Game game) {
            u.segmented(g, 48, 118, 600, 30, new String[]{"By electoral votes", "Closest", "Biggest swing", "Turnout change"}, sortBy, i -> sortBy = i, null);
            double x = 48, y = 160, w = DW - 96, h = 650;
            u.panel(g, x, y, w, h, null);
            String[] head = {"State", "EV", "Winner", "2028 margin", "2024 margin", "Swing", "Votes 2028", "Votes 2024", "Turnout", "Senate"};
            double[] cx = {x + 20, x + 230, x + 300, x + 470, x + 600, x + 730, x + 860, x + 1020, x + 1180, x + 1290};
            for (int i = 0; i < head.length; i++) label(g, head[i], cx[i], y + 24, C_DIM);
            Integer[] idx = new Integer[NS];
            for (int s = 0; s < NS; s++) idx[s] = s;
            Arrays.sort(idx, (a, b) -> sortBy == 0 ? STATES[b].ev - STATES[a].ev : sortBy == 1 ? Double.compare(Math.abs(m28[a]), Math.abs(m28[b])) : sortBy == 2 ? Double.compare(Math.abs(swing[b]), Math.abs(swing[a])) : Double.compare(turn28[b] / turn24[b], turn28[a] / turn24[a]));
            double off = u.beginScroll(g, "resT", x, y + 34, w, h - 40);
            int h0 = u.hits.size();
            double ry = y + 38 - off;
            for (int s : idx) {
                final int st = s;
                boolean hov = u.hover(x + 6, ry, w - 12, 22);
                if (hov) { g.setColor(new Color(255, 255, 255, 12)); g.fill(new RoundRectangle2D.Double(x + 6, ry, w - 12, 22, 6, 6)); }
                int win = winner(game, s);
                Color wc = win == DEM ? C_DEM_L : win == REP ? C_REP_L : C_IND;
                Camp wk = win == DEM ? game.nominee(DEM) : win == REP ? game.nominee(REP) : game.indep();
                text(g, STATES[s].name, cx[0], ry + 15, F_XS, C_TEXT);
                text(g, String.valueOf(STATES[s].ev), cx[1], ry + 15, F_XS, C_TEXT);
                text(g, wk != null ? wk.last() : PARTY[win], cx[2], ry + 15, F_XSB, wc);
                text(g, margin(m28[s]), cx[3], ry + 15, F_XSB, m28[s] >= 0 ? C_DEM_L : C_REP_L);
                text(g, margin(m24[s]), cx[4], ry + 15, F_XS, C_DIM);
                text(g, (swing[s] >= 0 ? "D+" : "R+") + fmt1(Math.abs(swing[s])), cx[5], ry + 15, F_XS, swing[s] >= 0 ? C_DEM_L : C_REP_L);
                text(g, compact(turn28[s]), cx[6], ry + 15, F_XS, C_TEXT);
                text(g, compact(turn24[s]), cx[7], ry + 15, F_XS, C_DIM);
                text(g, signed1((turn28[s] / turn24[s] - 1) * 100) + "%", cx[8], ry + 15, F_XS, C_TEXT);
                for (SenateRace sr : game.senate) if (sr.state == s) text(g, (sr.margin > 0 ? "D" : "R") + " " + margin(sr.margin).substring(2) + ((sr.margin > 0) != (sr.party == DEM) ? " flip" : ""), cx[9], ry + 15, F_XS, sr.margin > 0 ? C_DEM_L : C_REP_L);
                u.hit(x + 6, ry, w - 12, 22, () -> { sel = st; grpState = st; tab = 0; u.resetScroll("res"); }, "Open " + STATES[s].name);
                ry += 23;
            }
            u.clipHits(h0, y + 34, y + h - 6);
            u.endScroll(g, "resT", NS * 23 + 10);
        }

        // ---------------------------------------------------------------- groups
        static void groupsTab(Battleground2028 u, Graphics2D g, Game game) {
            double x = 48, y = 118, w = DW - 96, h = 700;
            u.panel(g, x, y, w, h, null);
            int s = grpState;
            text(g, s < 0 ? "How America voted" : "How " + STATES[s].name + " voted", x + 20, y + 32, F_H2, Color.white);
            u.button(g, x + w - 220, y + 14, 200, 26, s < 0 ? "Pick a state on the Map tab" : "Back to the nation", s < 0 ? B_OFF : B_NORM, s < 0 ? null : () -> grpState = -1);
            text(g, "Exit-poll style estimates from the game's electorate model. D minus R among each group's voters.", x + 20, y + 52, F_XS, C_DIM);
            String[] head = {"Group", "2024", "2028", "Swing", s < 0 ? "Share of voters 2024 -> 2028" : ""};
            double[] cx = {x + 20, x + 330, x + 450, x + 570, x + 790};
            for (int i = 0; i < head.length; i++) label(g, head[i], cx[i], y + 84, C_DIM);
            double ry = y + 92;
            for (int gr = 0; gr < NG; gr++) {
                double a = s < 0 ? g24[gr] : stateGroup24[s][gr], b = s < 0 ? g28[gr] : stateGroup28[s][gr];
                if (Double.isNaN(a) || Double.isNaN(b)) continue;
                double sw = b - a;
                text(g, GROUP[gr], cx[0], ry + 15, F_XS, C_TEXT);
                text(g, margin(a), cx[1], ry + 15, F_XS, a >= 0 ? C_DEM_L : C_REP_L);
                text(g, margin(b), cx[2], ry + 15, F_XSB, b >= 0 ? C_DEM_L : C_REP_L);
                // swing bar
                double bx = cx[3], bw = 120;
                sbar(g, bx, ry + 8, bw, 7, sw / 15.0, C_REP, C_DEM);
                text(g, (sw >= 0 ? "D+" : "R+") + fmt1(Math.abs(sw)), bx + bw + 8, ry + 15, F_XS, sw >= 0 ? C_DEM_L : C_REP_L);
                if (s < 0) {
                    text(g, fmt1(gs24[gr] * 100) + "%  ->  " + fmt1(gs28[gr] * 100) + "%", cx[4], ry + 15, F_XS, C_DIM);
                }
                ry += 20;
            }
        }

        // ---------------------------------------------------------------- turnout
        static void turnoutTab(Battleground2028 u, Graphics2D g, Game game) {
            double x = 48, y = 118, w = 760, h = 700;
            u.panel(g, x, y, w, h, "Turnout");
            double cy = y + 44;
            String[][] kv = {{"Votes cast in 2024", thousands(Math.round(tot24))}, {"Votes cast in 2028", thousands(Math.round(tot28))},
                    {"Change", signed1((tot28 / tot24 - 1) * 100) + "%"}, {"Turnout of eligible citizens (est.)", fmt1(tot28 / Math.max(1, elig28) * 100) + "%"}};
            for (String[] r : kv) { text(g, r[0], x + 20, cy + 14, F_S, C_TEXT); textR(g, r[1], x + w - 20, cy + 14, F_BB, Color.white); cy += 22; }
            cy += 4;
            label(g, "Turnout rate by group: 2024 -> 2028", x + 20, cy + 10, C_GOLD);
            cy += 18;
            for (int gr = 0; gr < NG; gr++) {
                double a = gt24[gr], b = gt28[gr];
                if (a <= 0) continue;
                textFit(g, GROUP[gr], x + 20, cy + 11, 200, F_XS, C_TEXT);
                bar(g, x + 230, cy + 4, 300, 6, b, b >= a ? C_GOOD : C_BAD);
                g.setColor(Color.white);
                g.fill(new Rectangle2D.Double(x + 230 + 300 * a - 1, cy + 1, 2, 12));
                textR(g, Math.round(a * 100) + "% -> " + Math.round(b * 100) + "%", x + w - 20, cy + 11, F_XS, b >= a ? C_GOOD : C_BAD);
                cy += 17;
            }
            double x2 = 830, w2 = DW - 48 - x2;
            u.panel(g, x2, y, w2, h, "Your ground game");
            Camp me = game.me();
            cy = y + 44;
            if (me != null) {
                double doors = 0, added = 0;
                for (int s = 0; s < NS; s++) { doors += me.doorsT[s]; added += gotvVotes[s]; }
                String[][] f = {{"Doors knocked", compact(doors)}, {"Votes your turnout program added (est.)", compact(added)}, {"Peak volunteers", compact(maxOf(me.volHist))}};
                for (String[] r : f) { text(g, r[0], x2 + 20, cy + 14, F_S, C_TEXT); textR(g, r[1], x2 + w2 - 20, cy + 14, F_BB, Color.white); cy += 24; }
                cy += 10;
                label(g, "Where your field program mattered most", x2 + 20, cy + 10, C_GOLD);
                cy += 18;
                Integer[] idx = new Integer[NS];
                for (int s = 0; s < NS; s++) idx[s] = s;
                Arrays.sort(idx, (a, b) -> Double.compare(gotvVotes[b], gotvVotes[a]));
                for (int i = 0; i < 14; i++) {
                    int s = idx[i];
                    if (gotvVotes[s] < 100) break;
                    text(g, STATES[s].name, x2 + 20, cy + 13, F_XS, C_TEXT);
                    text(g, compact(me.doorsT[s]) + " doors", x2 + 220, cy + 13, F_XS, C_DIM);
                    text(g, "+" + compact(gotvVotes[s]) + " votes", x2 + 360, cy + 13, F_XSB, C_GOOD);
                    double mg = Math.abs(m28[s]) / 100 * turn28[s];
                    if (gotvVotes[s] > mg && (me.party == DEM) == (m28[s] > 0)) chip(g, "MARGIN OF VICTORY", x2 + 500, cy + 1, C_GOLD, true);
                    cy += 19;
                }
            }
        }

        static double maxOf(double[] a) { double m = 0; for (double v : a) m = Math.max(m, v); return m; }

        // ---------------------------------------------------------------- Congress
        static void congressTab(Battleground2028 u, Graphics2D g, Game game) {
            double x = 48, y = 118, w = 900, h = 700;
            u.panel(g, x, y, w, h, "The Senate");
            int sd = General.senateSeats(game);
            text(g, sd + " D  -  " + (100 - sd) + " R", x + 20, y + 58, F_NUMXL, Color.white);
            text(g, sd >= 51 ? "Democratic control" : sd == 50 ? "50-50: the vice president breaks ties" : "Republican control", x + 330, y + 52, F_H3, sd >= 51 ? C_DEM_L : sd == 50 ? C_GOLD : C_REP_L);
            text(g, "Before the election: " + game.set.midSenateD + " D - " + (100 - game.set.midSenateD) + " R", x + 330, y + 70, F_XS, C_DIM);
            double cy = y + 92;
            String[] head = {"State", "Seat", "Result", "Margin", ""};
            double[] cx = {x + 20, x + 180, x + 520, x + 640, x + 760};
            for (int i = 0; i < head.length; i++) label(g, head[i], cx[i], cy, C_DIM);
            cy += 8;
            List<SenateRace> races = new ArrayList<SenateRace>(game.senate);
            Collections.sort(races, (a, b) -> Double.compare(Math.abs(a.margin), Math.abs(b.margin)));
            double off = u.beginScroll(g, "resS", x, cy, w, h - (cy - y) - 10);
            int h0 = u.hits.size();
            double ry = cy - off;
            for (SenateRace r : races) {
                boolean flip = (r.margin > 0) != (r.party == DEM);
                text(g, STATES[r.state].name, cx[0], ry + 15, F_XS, C_TEXT);
                textFit(g, r.incumbent + " (" + (r.party == DEM ? "D" : "R") + ")" + (r.open ? " - open" : ""), cx[1], ry + 15, 330, F_XS, C_DIM);
                text(g, r.margin > 0 ? "Democrat wins" : "Republican wins", cx[2], ry + 15, F_XSB, r.margin > 0 ? C_DEM_L : C_REP_L);
                text(g, margin(r.margin), cx[3], ry + 15, F_XS, C_TEXT);
                if (flip) chip(g, r.margin > 0 ? "D PICKUP" : "R PICKUP", cx[4], ry + 3, r.margin > 0 ? C_DEM : C_REP, true);
                ry += 21;
            }
            u.clipHits(h0, cy, y + h - 10);
            u.endScroll(g, "resS", races.size() * 21 + 10);
            double x2 = 968, w2 = DW - 48 - x2;
            u.panel(g, x2, y, w2, h, "The House (estimate)");
            int hd = game.houseSeatsD;
            text(g, hd + " D  -  " + (435 - hd) + " R", x2 + 20, y + 58, F_NUMXL, Color.white);
            text(g, hd >= 218 ? "Democratic majority" : "Republican majority", x2 + 20, y + 84, F_H3, hd >= 218 ? C_DEM_L : C_REP_L);
            double yy = y + 112;
            String[][] kv = {{"National House vote", margin(game.houseMargin)}, {"Seats after the 2026 midterms", game.set.midHouseD + " D - " + (435 - game.set.midHouseD) + " R"},
                    {"Change", (hd - game.set.midHouseD >= 0 ? "+" : "") + (hd - game.set.midHouseD) + " Democratic seats"}};
            for (String[] r : kv) { text(g, r[0], x2 + 20, yy + 14, F_S, C_TEXT); textR(g, r[1], x2 + w2 - 20, yy + 14, F_BB, Color.white); yy += 24; }
            yy += 10;
            // seat bar
            g.setColor(C_DEM);
            g.fill(new RoundRectangle2D.Double(x2 + 20, yy, (w2 - 40) * hd / 435.0, 18, 6, 6));
            g.setColor(C_REP);
            g.fill(new RoundRectangle2D.Double(x2 + 20 + (w2 - 40) * hd / 435.0, yy, (w2 - 40) * (435 - hd) / 435.0, 18, 6, 6));
            g.setColor(Color.white);
            g.fill(new Rectangle2D.Double(x2 + 20 + (w2 - 40) * 218 / 435.0 - 1, yy - 4, 2, 26));
            yy += 40;
            wrap(g, "House seats are estimated from the national House vote, which follows the presidential race with some ticket-splitting, and the map's built-in tilt toward Republicans of about a point.", x2 + 20, yy, w2 - 40, F_XS, C_DIM);
            if (game.contingent >= 0) {
                yy += 70;
                label(g, "The contingent election", x2 + 20, yy, C_GOLD);
                wrap(g, "No candidate reached 270 electoral votes, so under the 12th Amendment the new House chose the president, each state delegation casting one vote. It chose the " + PARTY[game.contingent] + " nominee.", x2 + 20, yy + 18, w2 - 40, F_S, C_TEXT);
            }
        }
    }

    // =====================================================================
    //  34.  THE OPTIONAL TUTORIAL: a guided tour of headquarters, then tips the first time each part of the game appears
    // =====================================================================
    static final class Tutorial {
        // ---- the headquarters tour ----
        static boolean touring;
        static int step;
        static boolean condAtEntry;
        static double doneAt = -1;
        static int[] snapAct;
        static int snapSel, snapMode, snapRtab;

        /** One step of the tour: a highlighted region, what it is, and (optionally) something to try. */
        static final class Step {
            String title, body, tryIt;
            double[] rect;
            Cond cond;
            Step(String title, String body, double[] rect, String tryIt, Cond cond) { this.title = title; this.body = body; this.rect = rect; this.tryIt = tryIt; this.cond = cond; }
        }

        interface Cond { boolean met(Battleground2028 u, Game g, Camp me); }

        static boolean changed(int[] a, int[] b) {
            if (a == null || b == null) return false;
            for (int i = 0; i < Math.min(a.length, b.length); i++) if (a[i] != b[i]) return true;
            return false;
        }

        static Settings settings(Battleground2028 u) { return u.game != null && (u.screen != SC_SETUP && u.screen != SC_PICK && u.screen != SC_CREATE && u.screen != SC_FIELD && u.screen != SC_PLATFORM) ? u.game.set : u.setup; }

        static boolean on(Battleground2028 u) { Settings s = settings(u); return s != null && s.tutorial; }

        static boolean seen(Settings s, String key) { return s.tipsSeen != null && s.tipsSeen.contains(key); }

        static void markSeen(Settings s, String key) {
            if (s.tipsSeen == null) s.tipsSeen = new ArrayList<String>();
            if (!s.tipsSeen.contains(key)) s.tipsSeen.add(key);
        }

        /** Replays the tour in the game in progress (from Help). */
        static void replay(Battleground2028 u) {
            if (u.game == null) return;
            u.game.set.tutorial = true;
            u.game.set.tourDone = false;
            touring = false;
            u.go(SC_HQ);
        }

        static List<Step> steps(Battleground2028 u, Game g, Camp me) {
            List<Step> l = new ArrayList<Step>();
            boolean prim = Hq.primaryView(g);
            boolean ind = me.party == IND;
            boolean ballot = Ballot.running(me) && Ballot.count(me) < NS;
            final double dashY = Hq.MAPY + Hq.MAPH + 30;
            l.add(new Step("Welcome to headquarters",
                    "This short tour shows you around. Each step points at one part of the screen; some ask you to try something and move on by themselves when you do. "
                            + "Press Next to move on, or Skip tour to stop. You can replay it any time from Help (F1).", null, null, null));
            String standing = prim ? "your standing in the primary (national poll and delegates won)"
                    : ind ? "your national poll (15% gets you onto the debate stage) and " + (ballot ? "the states where you've qualified for the ballot" : "the states you lead")
                    : "the national polls and the electoral votes you'd win if the public polls are right";
            l.add(new Step("The top bar",
                    "Your cash on hand, " + standing + ", your national name recognition, your base's enthusiasm and your candidate's stamina. Hover over any of them for details. "
                            + (prim ? "Two years out, primary polls mostly measure name recognition - raise it and you'll climb." : "Name recognition grows with ads, visits, doors, podcasts and debates."),
                    new double[]{392, 2, 690, 54}, null, null));
            l.add(new Step("End the turn",
                    "When your orders are in, end the turn here or press Space. Each turn is one week; the last two weeks before Election Day are played one day at a time. Don't press it yet.",
                    new double[]{DW - 16 - 160, 9, 160, 40}, null, null));
            l.add(new Step("The candidate's schedule",
                    "Three slots a day: morning, afternoon and evening. Rallies, town halls and other events happen in the state on the little button; podcasts, TV and call time happen from anywhere. "
                            + "The biggest podcasts only book candidates people have heard of. Long same-day flights wear the candidate out.",
                    new double[]{Hq.LX, Hq.TOP, Hq.LW, 484}, "click a slot and pick an event.",
                    (uu, gg, m) -> uu.popup != null || changed(snapAct, m.act)));
            l.add(new Step("The map",
                    "Click a state to work it: the right-hand panel switches to that state. Right-click a state to send the candidate there in the next free slot.",
                    new double[]{Hq.MX, Hq.MAPY, Hq.MW, Hq.MAPH}, "click a state.",
                    (uu, gg, m) -> Hq.sel >= 0 && Hq.sel != snapSel));
            l.add(new Step("Map views",
                    "Switch what the map shows: your spending, your own polls, the public polls and your volunteers" + (prim ? ", plus the primary calendar" : "") + (ballot ? ", plus ballot access" : "")
                            + ". Faint colours mean old or thin polling.",
                    new double[]{Hq.MX, Hq.TOP, Hq.MW, 32}, "pick another view.",
                    (uu, gg, m) -> Hq.mapMode != snapMode));
            l.add(new Step("The state panel",
                    "Everything about the selected state: its polls, your name recognition there and your operation. Ads buys TV by media market (prices rise toward November) and sets your digital budget; "
                            + "Field opens offices and hires organisers; Voters shows who lives there and what they care about.",
                    new double[]{Hq.RX, Hq.TOP, Hq.RW, Hq.BOT - Hq.TOP}, "open the Ads tab.",
                    (uu, gg, m) -> Hq.rtab != snapRtab));
            l.add(new Step("Money",
                    "Cash, where it comes from and how fast big donors are tapping out; the size of your headquarters; " + (me.c.selfMax > 0 ? "self-funding (a weekly transfer, or a one-time check); " : "")
                            + "national TV; and your digital plan: split the online budget across platforms - Facebook skews older, Instagram, YouTube and TikTok younger - and choose what each one says.",
                    new double[]{Hq.LX, Hq.TOP, Hq.LW, Hq.BOT - Hq.TOP}, "open the Money tab (or press 2).",
                    (uu, gg, m) -> Hq.tab == 1));
            l.add(new Step("Field",
                    (ind && Ballot.running(me) ? "As an independent, start here: fund petitions in every state before its filing deadline, or you'll only be a write-in there. Below that, your ground game: " : "Your ground game: ")
                            + "offices hold organisers, organisers recruit volunteers, and volunteers knock doors, make calls and send texts. Contacts persuade a few voters and, in the final weeks, turn out your supporters.",
                    new double[]{Hq.LX, Hq.TOP, Hq.LW, Hq.BOT - Hq.TOP}, "open the Field tab (or press 3).",
                    (uu, gg, m) -> Hq.tab == 2));
            l.add(new Step("Message",
                    "Each week pick a frame (your message) and an attack line, or stay positive. Issue focus sets one main issue and two secondary ones: credibility on them builds every week you stay on them. "
                            + "Promises win over some voters and anger others - and they're permanent.",
                    new double[]{Hq.LX, Hq.TOP, Hq.LW, Hq.BOT - Hq.TOP}, "open the Message tab (or press 4).",
                    (uu, gg, m) -> Hq.tab == 3));
            l.add(new Step("Polls",
                    "Commission polls - bigger samples and live callers cost more but miss less - or set your pollster to run on its own. The Issues view shows what voters care about, whom they trust on each issue and whether they agree with you.",
                    new double[]{Hq.LX, Hq.TOP, Hq.LW, Hq.BOT - Hq.TOP}, "open the Polls tab (or press 5).",
                    (uu, gg, m) -> Hq.tab == 4));
            l.add(new Step("Campaign",
                    "Hand any part of the campaign to your staff: the schedule, ads, field, polling or the message. Also here: your debates, rally crowds and podcast audiences, opposition research and endorsements.",
                    new double[]{Hq.LX, Hq.TOP, Hq.LW, Hq.BOT - Hq.TOP}, "open the Campaign tab (or press 6).",
                    (uu, gg, m) -> Hq.tab == 5));
            l.add(new Step("The race and the news",
                    (prim ? "The race for your party's nomination, with the next contests, " : "The race for 270 and the national polling trend, ")
                            + "and this week's news. After every turn a short recap appears over the map: money, polls, volunteers, rallies and podcasts.",
                    new double[]{Hq.MX, dashY, Hq.MW, Hq.BOT - dashY}, null, null));
            l.add(new Step("You're ready",
                    "Fill the " + (g.daily() ? "day" : "week") + ", spend, then press END " + (g.daily() ? "DAY" : "WEEK") + ". With the tutorial on, a tip will appear the first time you reach a debate, a primary night, a fight over an endorsement, "
                            + "the final stretch and the results. Replay this tour any time from Help (F1).", null, null, null));
            return l;
        }

        static void enterStep(Battleground2028 u, Game g, Camp me) {
            snapAct = me.act.clone();
            snapSel = Hq.sel;
            snapMode = Hq.mapMode;
            snapRtab = Hq.rtab;
            doneAt = -1;
            List<Step> l = steps(u, g, me);
            if (step >= 0 && step < l.size()) {
                Step s = l.get(step);
                if (s.title.startsWith("The candidate's schedule")) Hq.tab = 0;
                if (s.title.startsWith("The state panel") && Hq.sel < 0) { Hq.sel = me.c.home >= 0 ? me.c.home : stateIdx("PA"); snapSel = Hq.sel; }
                condAtEntry = s.cond != null && s.cond.met(u, g, me);
            }
        }

        static void go(Battleground2028 u, int to) {
            Game g = u.game;
            Camp me = g.me();
            List<Step> l = steps(u, g, me);
            if (to >= l.size() || to < 0) { finish(u); return; }
            step = to;
            enterStep(u, g, me);
        }

        static void finish(Battleground2028 u) {
            touring = false;
            if (u.game != null) u.game.set.tourDone = true;
        }

        // ---- drawing (called once per frame, on top of everything but tooltips) ----
        static void draw(Battleground2028 u, Graphics2D g) {
            if (!on(u)) { touring = false; return; }
            Settings s = settings(u);
            int saved = u.layer;
            u.layer = u.topLayer;
            try {
                if (u.screen == SC_HQ && u.game != null && u.game.me() != null && !Hq.resolving && !Hq.fastForward) {
                    Game game = u.game;
                    Camp me = game.me();
                    if (!touring && !s.tourDone && u.modal == null && game.pending.isEmpty()) { touring = true; step = 0; enterStep(u, game, me); }
                    if (touring) {
                        if (u.modal == null && u.popup == null) tour(u, g, game, me);     // out of the way while a menu is open
                        else if (u.modal != null) tips(u, g, s);
                        return;
                    }
                }
                tips(u, g, s);
            } finally {
                u.layer = saved;
            }
        }

        static void tour(Battleground2028 u, Graphics2D g, Game game, Camp me) {
            List<Step> l = steps(u, game, me);
            if (step >= l.size()) { finish(u); return; }
            Step st = l.get(step);
            boolean met = st.cond != null && st.cond.met(u, game, me);
            if (met && !condAtEntry && doneAt < 0) doneAt = u.tsec;
            if (!met && condAtEntry) condAtEntry = false;
            if (doneAt >= 0 && u.tsec - doneAt > 0.9 && u.popup == null) { go(u, step + 1); return; }
            // spotlight: dim everything except the highlighted region
            g.setColor(new Color(2, 6, 14, st.rect == null ? 150 : 120));
            if (st.rect == null) g.fill(new Rectangle2D.Double(0, 0, DW, DH));
            else {
                double rx = st.rect[0] - 4, ry = st.rect[1] - 4, rw = st.rect[2] + 8, rh = st.rect[3] + 8;
                g.fill(new Rectangle2D.Double(0, 0, DW, Math.max(0, ry)));
                g.fill(new Rectangle2D.Double(0, ry + rh, DW, Math.max(0, DH - ry - rh)));
                g.fill(new Rectangle2D.Double(0, ry, Math.max(0, rx), rh));
                g.fill(new Rectangle2D.Double(rx + rw, ry, Math.max(0, DW - rx - rw), rh));
            }
            if (st.rect != null) {
                double pulse = 0.55 + 0.45 * Math.sin(u.tsec * 4);
                g.setStroke(new BasicStroke(2.5f));
                g.setColor(alpha(C_GOLD, 0.55 + 0.4 * pulse));
                g.draw(new RoundRectangle2D.Double(st.rect[0] - 4, st.rect[1] - 4, st.rect[2] + 8, st.rect[3] + 8, 12, 12));
                g.setStroke(new BasicStroke(1f));
            }
            // the card
            double w = st.rect == null ? 560 : 400;
            double bodyH = wrapHeight(g, st.body, w - 36, F_S);
            double tryH = st.tryIt != null ? wrapHeight(g, "Try it: " + st.tryIt, w - 36, F_XSB) + 10 : 0;
            double h = 66 + bodyH + tryH + 62;
            double[] p = place(st.rect, w, h);
            double x = p[0], y = p[1];
            card(g, x, y, w, h);
            u.hit(x, y, w, h, () -> { }, null);
            label(g, "TUTORIAL  " + (step + 1) + " / " + l.size(), x + 18, y + 22, C_GOLD);
            text(g, st.title, x + 18, y + 50, F_H3, Color.white);
            double cy = y + 70 + wrap(g, st.body, x + 18, y + 70, w - 36, F_S, C_TEXT);
            if (st.tryIt != null) {
                boolean done = doneAt >= 0 || met && condAtEntry;
                wrap(g, done ? "Done!" : "Try it: " + st.tryIt, x + 18, cy + 10, w - 36, F_XSB, done ? C_GOOD : C_GOLD);
            }
            double by = y + h - 46;
            boolean last = step == l.size() - 1;
            u.button(g, x + w - 18 - 120, by, 120, 32, last ? "Start" : "Next", B_GOLD, () -> go(u, step + 1), last ? "Close the tour and start campaigning." : null);
            if (step > 0) u.button(g, x + w - 18 - 120 - 90, by, 80, 32, "Back", B_NORM, () -> go(u, step - 1));
            if (!last) u.button(g, x + 18, by, 110, 32, "Skip tour", B_GHOST, () -> finish(u), "End the tour. Tips will still appear the first time each part of the game comes up.");
        }

        /** Puts the card beside the highlighted region, wherever it fits. */
        static double[] place(double[] r, double w, double h) {
            if (r == null) return new double[]{(DW - w) / 2, (DH - h) / 2};
            double x, y;
            if (r[0] + r[2] + 16 + w <= DW - 8) { x = r[0] + r[2] + 16; y = r[1]; }
            else if (r[0] - 16 - w >= 8) { x = r[0] - 16 - w; y = r[1]; }
            else if (r[1] + r[3] + 16 + h <= DH - 8) { x = r[0] + r[2] / 2 - w / 2; y = r[1] + r[3] + 16; }
            else { x = r[0] + r[2] / 2 - w / 2; y = r[1] - 16 - h; }
            if (r[3] < 80 && r[1] < 100) { y = r[1] + r[3] + 16; x = r[0] + r[2] / 2 - w / 2; }      // thin bars along the top: card goes underneath
            return new double[]{clamp(x, 8, DW - w - 8), clamp(y, 8, DH - h - 8)};
        }

        static void card(Graphics2D g, double x, double y, double w, double h) {
            g.setColor(new Color(0, 0, 0, 120));
            g.fill(new RoundRectangle2D.Double(x + 4, y + 8, w, h, 14, 14));
            RoundRectangle2D rr = new RoundRectangle2D.Double(x, y, w, h, 14, 14);
            g.setPaint(new GradientPaint((float) x, (float) y, rgb(0x1a2a46), (float) x, (float) (y + h), rgb(0x0e1830)));
            g.fill(rr);
            g.setColor(alpha(C_GOLD, 0.8));
            g.setStroke(new BasicStroke(1.4f));
            g.draw(rr);
            g.setStroke(new BasicStroke(1f));
            g.setColor(C_GOLD);
            g.fill(new RoundRectangle2D.Double(x + 18, y, 70, 4, 4, 4));
        }

        // ---- first-time tips ----
        static final int A_TOP = 0, A_TOPRIGHT = 1, A_BOTLEFT = 2, A_BOTCENTER = 3;

        /** Which tip (if any) applies right now: {key, title, body, anchor}. */
        static Object[] currentTip(Battleground2028 u) {
            Game g = u.game;
            Modal m = u.modal;
            switch (u.screen) {
                case SC_SETUP:
                    return new Object[]{"setup", "Setting up your race", "New to the game? Try General election only on Easy: it's shorter. A full campaign starts two years out, the week after the 2026 midterms, and runs through the primaries. "
                            + "Pick a scenario, then choose your candidate.", A_BOTCENTER};
                case SC_PICK:
                    return new Object[]{"pick", "Choosing a candidate", "Click a card to see a candidate's ratings, record, strengths and weaknesses. Edit platform tweaks their starting positions; the last card builds your own candidate; "
                            + "the Independent tab runs outside the two parties. When you're happy, press Run as.", A_BOTCENTER};
                case SC_FIELD:
                    if (FieldScreen.opponentsOnly)
                        return new Object[]{"field_ind", "Your opponents", "Click the Democrat and the Republican you'd like to face, or keep the likeliest nominees. Then press Start the campaign.", A_BOTCENTER};
                    return new Object[]{"field", "Who's running", "Optional: click candidates to keep them out of the race, and lock in the other party's nominee if you like. Or just press Start the campaign.", A_BOTCENTER};
                case SC_CREATE:
                    return new Object[]{"create", "Building a candidate", "Choose a look, a name, a home state and a party, then spend points on ratings (Unlimited lifts the cap). Hover over any rating to see what it does. Run as starts the campaign.", A_BOTCENTER};
                case SC_VP:
                    return new Object[]{"vp", "Choosing a running mate", "Each card shows the projected effect on your ticket nationally and in the running mate's home state. Vetting costs money but reveals scandal risk before reporters do.", A_BOTCENTER};
                case SC_NEGOT:
                    return new Object[]{"negot", "Debate negotiations", "Propose how many debates, when, whether there's a vice-presidential debate, the networks and the rules. Your opponent counters; strong debaters want more debates, weak ones fewer.", A_BOTCENTER};
                case SC_DEBATE: {
                    DebateRun r = DebateView.run;
                    if (r != null && r.myIdx >= 0)
                        return new Object[]{"debate", "Debate night", "For each question, pick an approach (keys 1-6), then the line you'll deliver (1-3). Approaches beat each other like rock-paper-scissors; your prep, charisma and intellect decide how well it lands. "
                                + "If a rival comes after you, pick a response (1-4). The dial shows how a focus group reacts.", A_TOPRIGHT};
                    return null;
                }
                case SC_NIGHT:
                    return new Object[]{"night", "Election Night", "States are called as the votes come in. Use the speed buttons to fast-forward, or End to jump to the final result.", A_BOTLEFT};
                case SC_RESULTS:
                    return new Object[]{"results", "Exploring the results", "Click any state for its vote, its swing since 2024, turnout and how each group voted. The tabs compare every state, every group, turnout and the races for Congress.", A_BOTCENTER};
                case SC_HQ: {
                    if (g == null || g.me() == null) return null;
                    if (m != null && m.title != null) {
                        if (m.title.endsWith("drops out") && m.buttons != null && m.buttons.length == 2)
                            return new Object[]{"endorse", "Fighting for an endorsement", "A rival has quit. The table shows where their voters are expected to go. Switch on offers - policy promises, a cabinet post, the vice-presidential slot, a convention speech - and watch your odds change. "
                                    + "Rivals are bidding too, and promises are binding.", A_TOP};
                        if (m.title.startsWith("Primary night") || m.title.startsWith("Results:"))
                            return new Object[]{"primary", "Primary night", "Delegates are shared out by vote share (Democrats need 15% to win any; many later Republican contests are winner-take-all). Beating expectations brings momentum: money, press and voters follow.", A_TOP};
                        if (m.title.startsWith("Your closing argument"))
                            return new Object[]{"closing", "The closing argument", "The last thing you want voters to hear. It builds every day you stick with it, and the fit score shows how well it suits you and the moment. Changing it later throws most of that away.", A_TOP};
                        if (m.stacked && m.face != null && m.buttons != null && m.buttons.length >= 2 && !m.title.startsWith("When does"))
                            return new Object[]{"event", "Events", "The world intervenes. Pick a response: each one moves your platform and particular groups of voters, so there's rarely a free choice.", A_TOP};
                        return null;
                    }
                    if (g.daily())
                        return new Object[]{"daily", "The final stretch", "Each turn is now a single day. Plan the day's three events (the rest of the week can wait), keep your closing argument steady and pour everything into turnout.", A_TOPRIGHT};
                    return null;
                }
                default:
                    return null;
            }
        }

        static void tips(Battleground2028 u, Graphics2D g, Settings s) {
            Object[] t = currentTip(u);
            if (t == null) return;
            final String key = (String) t[0];
            if (seen(s, key)) return;
            if (u.screen == SC_HQ && u.popup != null) return;
            String title = (String) t[1], body = (String) t[2];
            int anchor = (Integer) t[3];
            double w = anchor == A_TOP || anchor == A_BOTCENTER ? 640 : 430;
            // screens with a quiet corner of their own: put the tip there, clear of the controls
            double[] spot = key.equals("setup") ? new double[]{548, -1, 500} : key.equals("vp") ? new double[]{686, 572, 560}
                    : key.equals("negot") ? new double[]{830, 420, 600} : key.equals("results") ? new double[]{1062, 560, 476} : null;
            if (spot != null) w = spot[2];
            double bodyH = wrapHeight(g, body, w - 36, F_S);
            double h = 56 + bodyH + 54;
            double x, y;
            switch (anchor) {
                case A_TOP: x = (DW - w) / 2; y = 8; break;
                case A_TOPRIGHT: x = DW - w - 16; y = 96; break;
                case A_BOTLEFT: x = 48; y = DH - h - 60; break;
                default: x = (DW - w) / 2; y = DH - h - 76; break;
            }
            if (spot != null) { x = spot[0]; if (spot[1] >= 0) y = spot[1]; }
            // a modal underneath: keep the tip clear of it
            if (u.modal != null && anchor == A_TOP) y = 8;
            card(g, x, y, w, h);
            u.hit(x, y, w, h, () -> { }, null);
            label(g, "TIP", x + 18, y + 22, C_GOLD);
            text(g, title, x + 54, y + 23, F_SB, Color.white);
            wrap(g, body, x + 18, y + 46, w - 36, F_S, C_TEXT);
            u.button(g, x + w - 18 - 100, y + h - 44, 100, 30, "Got it", B_GOLD, () -> markSeen(s, key));
            u.button(g, x + 18, y + h - 44, 150, 30, "Turn off tips", B_GHOST, () -> { s.tutorial = false; touring = false; }, "Switch the tutorial off for this campaign. You can replay the tour from Help.");
        }
    }

    // =====================================================================
    //  MAIN
    // =====================================================================
    public static void main(String[] args) {
        if (args != null && args.length > 0 && args[0].equals("test")) { Test.run(Arrays.copyOfRange(args, 1, args.length)); return; }
        SwingUtilities.invokeLater(new Runnable() { public void run() { launch(); } });
    }

    /** Headless checks used during development. */
    static final class Test {
        static void run(String[] a) {
            long t0 = System.currentTimeMillis();
            Elect.init();
            System.out.println("init " + (System.currentTimeMillis() - t0) + " ms, markets " + Elect.NM);
            String what = a.length > 0 ? a[0] : "calib";
            if (what.equals("calib")) calib();
            if (what.equals("ui")) ui(a.length > 1 ? a[1] : ".", a.length > 2 ? a[2] : "all");
            if (what.equals("portraits")) portraits(a.length > 1 ? a[1] : "portraits.png");
            if (what.equals("bigface")) {
                BufferedImage img = new BufferedImage(1260, 500, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                Portrait.hints(g);
                g.setColor(C_BG); g.fillRect(0, 0, 1260, 500);
                String[] ids = a.length > 2 ? a[2].split(",") : new String[]{"harris", "vance", "walz"};
                for (int i = 0; i < ids.length; i++) Portrait.draw(g, candById(ids[i]), 10 + i * 410, 10, 400, 480, 0.7 + i, 0.6, false, true);
                g.dispose();
                try { javax.imageio.ImageIO.write(img, "png", new File(a[1])); } catch (IOException e) { e.printStackTrace(); }
            }
            if (what.equals("game")) {
                int runs = a.length > 1 ? Integer.parseInt(a[1]) : 1;
                int mode = a.length > 2 ? Integer.parseInt(a[2]) : MODE_FULL;
                String cand = a.length > 3 ? a[3] : "shapiro";
                int scen = a.length > 4 ? Integer.parseInt(a[4]) : SC_BASE;
                aiOnly = a.length > 5 && a[5].contains("ai");
                primOnly = a.length > 5 && a[5].contains("p");
                for (int r = 0; r < runs; r++) game(1000 + r, mode, cand, scen, a.length > 5 && a[5].contains("v"));
            }
        }

        static void calib() {
            // reproduce 2024 with the 2024 composition
            double dv = 0, rv = 0;
            for (int s = 0; s < NS; s++) {
                double d = 0, t = 0;
                for (int c = 0; c < NCELL; c++) {
                    double w = Elect.cvap24[s][c] * Elect.turnB[s][c];
                    d += w * logistic(Elect.baseL[s][c]);
                    t += w;
                }
                dv += d; rv += t - d;
            }
            System.out.println("2024 national two-party D " + fmt1(dv / (dv + rv) * 100) + " (actual 49.2)");
            String[] blocs = BLOC;
            double[] bd = new double[NB], bt = new double[NB], b28 = new double[NB];
            double tot24 = 0, tot28 = 0;
            for (int s = 0; s < NS; s++) for (int c = 0; c < NCELL; c++) {
                int b = cellBloc(c);
                double w = Elect.cvap24[s][c] * Elect.turnB[s][c];
                bd[b] += w * logistic(Elect.baseL[s][c]); bt[b] += w; tot24 += w;
                b28[b] += Elect.cvap[s][c] * Elect.turnB[s][c]; tot28 += Elect.cvap[s][c] * Elect.turnB[s][c];
            }
            for (int b = 0; b < NB; b++) System.out.println(String.format("  %-32s D %5.1f  share24 %5.1f%%  share28 %5.1f%%  K %.2f", blocs[b], bd[b] / bt[b] * 100, bt[b] / tot24 * 100, b28[b] / tot28 * 100, Elect.blocK[b]));
            System.out.println("total votes 2024 " + compact(tot24) + "  2028 " + compact(tot28));
            {
                Game g0 = new Game();
                g0.set = new Settings();
                for (int i = 0; i < NI; i++) g0.salience[i] = 1;
                g0.env = 0;
                StringBuilder gb = new StringBuilder("generic 2028 composition, 2024 env + 2026 swing: nat " + margin(Polls.trueMargin(g0, -1)) + "\n  ");
                for (int gr = 0; gr < NG; gr++) gb.append(GROUP[gr]).append(' ').append(margin(Vote.groupMargin(g0, gr, -1))).append("  ");
                System.out.println(gb);
            }
            Settings set = new Settings();
            set.mode = MODE_GENERAL; set.candId = "shapiro"; set.opponentId = "vance"; set.seed = 1;
            Game g = Setup.newGame(set);
            double[][] t = Vote.general(g, null, null);
            double[] m = new double[NS];
            for (int s = 0; s < NS; s++) m[s] = Vote.margin(t[s]);
            int[] ev = Vote.electoral(m, null);
            System.out.println("Shapiro vs Vance baseline: nat " + margin(Polls.trueMargin(g, -1)) + "  EV D " + ev[0] + " R " + ev[1] + "  env " + fmt1(g.env));
            StringBuilder sb = new StringBuilder();
            for (int s = 0; s < NS; s++) sb.append(STATES[s].ab).append(' ').append(margin(m[s])).append(s % 8 == 7 ? "\n" : "   ");
            System.out.println(sb);
            for (String[] mu : new String[][]{{"harris", "vance"}, {"aoc", "vance"}, {"beshear", "vance"}, {"newsom", "rubio"}, {"buttigieg", "trumpjr"}, {"gallego", "desantis"}, {"kelly", "haley"}}) {
                set.candId = mu[0]; set.opponentId = mu[1];
                Game h = Setup.newGame(set);
                double[][] tt = Vote.general(h, null, null);
                double[] mm = new double[NS];
                for (int s = 0; s < NS; s++) mm[s] = Vote.margin(tt[s]);
                int[] e2 = Vote.electoral(mm, null);
                System.out.println(String.format("%-10s vs %-9s nat %-7s EV %d-%d   PA %s GA %s AZ %s MI %s WI %s NV %s NC %s",
                        mu[0], mu[1], margin(Polls.trueMargin(h, -1)), e2[0], e2[1], margin(mm[stateIdx("PA")]), margin(mm[stateIdx("GA")]),
                        margin(mm[stateIdx("AZ")]), margin(mm[stateIdx("MI")]), margin(mm[stateIdx("WI")]), margin(mm[stateIdx("NV")]), margin(mm[stateIdx("NC")])));
            }
            // decomposition of candidate effects (vote-weighted mean logit)
            for (Cand c : ROSTER) {
                if (c.party > REP) continue;
                set.candId = c.id; set.opponentId = c.party == DEM ? "vance" : "harris";
                Game h = Setup.newGame(set);
                Camp k = h.camps.get(0);
                double q = Vote.quality(h, k), is = 0, ap = 0, w = 0;
                for (int s = 0; s < NS; s++) for (int cc = 0; cc < NCELL; cc++) {
                    double n = Elect.cvap[s][cc] * Elect.turnB[s][cc] * Elect.elast[s][cc];
                    is += n * Vote.issueTerm(h, k, s, cc); ap += n * Vote.K_APPEAL * Vote.groupTerm(k.c.apG, k.c.apV, s, cc); w += n;
                }
                System.out.println(String.format("  %-22s qual %+.3f  issue %+.3f  appeal %+.3f   total %+.3f  enth %.2f  nat %s", c.name(), q, is / w, ap / w, q + is / w + ap / w, k.enthusiasm, margin(Polls.trueMargin(h, -1))));
            }
            // groups
            set.candId = "shapiro"; set.opponentId = "vance";
            Game h = Setup.newGame(set);
            StringBuilder gb = new StringBuilder("groups (Shapiro-Vance): ");
            for (int gr = 0; gr < NG; gr++) gb.append(GROUP[gr]).append(' ').append(margin(Vote.groupMargin(h, gr, -1))).append(" (").append(Math.round(Vote.groupSize(gr, -1) * 100)).append("%)  ");
            System.out.println(gb);
        }

        static boolean aiOnly, primOnly;

        static void shot(Battleground2028 u, String dir, String name) {
            BufferedImage img = u.snapshot(1600, 900);
            try { javax.imageio.ImageIO.write(img, "png", new File(dir, name + ".png")); } catch (IOException e) { e.printStackTrace(); }
            System.out.println("shot " + name);
        }

        static void ui(String dir, String which) {
            USMap.load();
            Battleground2028 u = new Battleground2028();
            u.tsec = 3.3;
            boolean all = which.equals("all");
            if (all || which.contains("home")) { u.go(SC_HOME); u.screenT = 5; shot(u, dir, "ui_home"); }
            if (all || which.contains("setup")) { u.go(SC_SETUP); shot(u, dir, "ui_setup"); }
            if (all || which.contains("pick")) { u.go(SC_PICK); Screens.pickParty = DEM; Screens.pickSel = 4; shot(u, dir, "ui_pick"); Screens.pickParty = REP; Screens.pickSel = 0; shot(u, dir, "ui_pick_r"); }
            if (all || which.contains("plat")) { PlatformEd.open(u, candById("shapiro"), SC_PICK); PlatformEd.plat[I_IMMIG] += 0.3; shot(u, dir, "ui_platform"); }
            if (all || which.contains("create")) { Creator.start(u, DEM); u.go(SC_CREATE); shot(u, dir, "ui_create"); }
            if (all || which.contains("tut")) {
                Settings st = new Settings();
                st.tutorial = true;
                u.setup = st;
                u.go(SC_SETUP); shot(u, dir, "ui_tut_setup");
                u.go(SC_PICK); Screens.pickParty = DEM; Screens.pickSel = 4; shot(u, dir, "ui_tut_pick");
                st.candId = "shapiro"; st.party = DEM; st.seed = 43;
                Screens.startGame(u, candById("shapiro"));
                u.modal = null;
                Game tg = u.game;
                tg.pending.clear();
                Hq.tab = 0; Hq.mapMode = MM_BLANK; Hq.rtab = 0;
                u.mx = -1; u.my = -1;
                int[] want = {0, 1, 2, 3, 4, 5, 6, 7, 8, 12, 13};
                for (int k = 0; k < want.length; k++) {
                    Tutorial.go(u, want[k]);
                    if (want[k] >= 7 && want[k] <= 11) Hq.tab = want[k] - 6;
                    shot(u, dir, "ui_tut_step" + want[k]);
                }
                Tutorial.finish(u);
                Hq.tab = 0;
                // first-time tips over a modal and on the debate stage
                Camp from = null;
                for (Camp k : tg.camps) if (k.party == DEM && k.active && k != tg.me() && (from == null || k.c.poll > from.c.poll)) from = k;
                if (from != null) {
                    double[] before = Vote.primaryNational(tg, DEM).clone();
                    Deals.endorse(u, tg, new Object[]{EV_ENDORSE, "", "", new String[0], from.idx, before, before.clone()});
                    shot(u, dir, "ui_tut_endorse");
                    u.modal = null;
                }
                Debate d0 = null;
                for (Debate dd : tg.debates) if (!dd.done && dd.party == DEM && (d0 == null || dd.week < d0.week)) d0 = dd;
                if (d0 != null) { tg.week = d0.week; DebateView.start(u, d0); shot(u, dir, "ui_tut_debate"); }
                Tutorial.touring = false;
                u.game = null;
            }
            if (all || which.contains("brief")) { Screens.briefTopic = 7; u.go(SC_BRIEF); shot(u, dir, "ui_brief"); Screens.briefTopic = 9; shot(u, dir, "ui_brief_sources"); }
            Settings set = new Settings();
            set.candId = "shapiro"; set.party = DEM; set.seed = 42;
            u.setup = set;
            if (all || which.contains("field")) {
                FieldScreen.open(u, candById("shapiro"), false);
                set.sitOut.add("cuban"); set.sitOut.add("carlson"); set.lockR = "rubio";
                shot(u, dir, "ui_field");
                set.sitOut.clear(); set.lockR = null;
                Settings si = new Settings();
                u.setup = si;
                FieldScreen.open(u, testIndependent(), true);
                shot(u, dir, "ui_field_ind");
                u.setup = set;
            }
            Screens.startGame(u, candById("shapiro"));
            if (all || which.contains("hq")) {
                shot(u, dir, "ui_hq_welcome");
                u.modal = null;
                shot(u, dir, "ui_hq0");
            }
            u.modal = null;
            Game g = u.game;
            Arrays.fill(g.set.staff, true);
            for (int w = 0; w < 30; w++) { g.pending.clear(); Week.resolve(g); }
            g.pending.clear();
            Hq.primCacheWeek = -1;
            if (all || which.contains("hq")) {
                for (int t = 0; t < 7; t++) { Hq.tab = t; Hq.mapMode = t % 5; shot(u, dir, "ui_hq_tab" + t); }
                Hq.tab = 3; Tabs.msgTab = 1; shot(u, dir, "ui_hq_issues"); Tabs.msgTab = 0;
                Hq.tab = 4; Tabs.pollTab = 1; shot(u, dir, "ui_hq_issuepolls"); u.scrolls.get("left")[4] = 600; shot(u, dir, "ui_hq_issuepolls2"); u.scrolls.get("left")[4] = 0; Tabs.pollTab = 0;
                Hq.tab = 1; u.scrolls.get("left")[4] = 520; shot(u, dir, "ui_hq_digital"); u.scrolls.get("left")[4] = 0;
                Hq.tab = 0; Camp me0 = g.me(); me0.act[4] = ACT_PODCAST; me0.param[4] = 0; u.mx = 300; u.my = 300; Tabs.podcastMenu(u, me0, 4); shot(u, dir, "ui_podcasts"); u.popup = null;
                // a rival drops out: the endorsement bid
                Camp from = null;
                for (Camp k : g.camps) if (k.party == DEM && k.active && k != me0 && k.announced && (from == null || g.primaryPoll[k.idx] > g.primaryPoll[from.idx])) from = k;
                if (from != null) {
                    double[] before = Vote.primaryNational(g, DEM).clone();
                    double[] after = before.clone();
                    after[from.idx] = 0;
                    Deals.endorse(u, g, new Object[]{EV_ENDORSE, "", "", new String[0], from.idx, before, after});
                    shot(u, dir, "ui_endorse");
                    Arrays.fill(Deals.pick, true);
                    shot(u, dir, "ui_endorse_offer");
                    u.modal = null;
                }
                Hq.rtab = 1; Hq.sel = stateIdx("NH"); shot(u, dir, "ui_hq_ads");
                Hq.rtab = 2; shot(u, dir, "ui_hq_field");
                Hq.rtab = 3; shot(u, dir, "ui_hq_voters");
                Hq.rtab = 0; Hq.sel = -1; shot(u, dir, "ui_hq_nation");
            }
            if (all || which.contains("debate")) {
                Debate d = null;
                for (Debate dd : g.debates) if (!dd.done && dd.party == DEM && (d == null || dd.week < d.week)) d = dd;
                if (d != null) {
                    g.week = d.week;
                    DebateView.start(u, d);
                    shot(u, dir, "ui_debate0");
                    DebateView.choose(u, 1);
                    shot(u, dir, "ui_debate_lines");
                    DebateView.chooseLine(u, 0);
                    DebateView.nextLine(u); DebateView.nextLine(u); DebateView.nextLine(u);
                    shot(u, dir, "ui_debate1");
                    // force a rebuttal moment
                    DebateRun rr = DebateView.run;
                    DebateView.revealing = false;
                    DebateView.shown = rr.lines.size();
                    if (!rr.over) {
                        rr.rebutBy = rr.myIdx == 0 ? 1 : 0; rr.rebutAttack = 9; rr.rebutPower = 1;
                        shot(u, dir, "ui_debate_rebut");
                        DebateView.rebut(u, 2);
                        for (int i = 0; i < 6; i++) DebateView.nextLine(u);
                        DebateView.revealing = false;
                        shot(u, dir, "ui_debate_rebut2");
                    }
                    u.go(SC_HQ);
                    g.week = Math.max(g.week, 31);
                }
            }
            if (all || which.contains("night")) {
                while (g.week < 70) { g.pending.clear(); for (Debate dd : Debates.thisWeek(g)) if (!dd.done) { DebateRun r = Debates.start(g, dd); r.myIdx = -1; while (!r.over) Debates.segment(g, r, -1); } Week.resolve(g); }
                List<Integer> idx = new ArrayList<Integer>();
                for (Contest c : g.contests) if (c.party == DEM && c.done && c.week == 69) idx.add(g.contests.indexOf(c));
                u.go(SC_HQ);
                u.modal = null;
                if (!idx.isEmpty()) { Hq.primaryNight(u, g, idx); shot(u, dir, "ui_primary_night"); }
                u.modal = null;
            }
            if (all || which.contains("general")) {
                Settings s2 = new Settings();
                s2.mode = MODE_GENERAL; s2.candId = "vance"; s2.party = REP; s2.opponentId = "newsom"; s2.seed = 7;
                Arrays.fill(s2.staff, true);
                u.setup = s2;
                Screens.startGame(u, candById("vance"));
                u.modal = null;
                Game g2 = u.game;
                g2.pending.clear();
                Screens.vpSel = 3;
                u.go(SC_VP); shot(u, dir, "ui_vp");
                u.go(SC_NEGOT); shot(u, dir, "ui_negot");
                u.go(SC_HQ);
                for (int w = 0; w < 5; w++) { g2.pending.clear(); for (Debate dd : Debates.thisWeek(g2)) if (!dd.done) { DebateRun r = Debates.start(g2, dd); r.myIdx = -1; while (!r.over) Debates.segment(g2, r, -1); } Week.resolve(g2); }
                g2.pending.clear();
                Hq.mapMode = MM_PUBLIC; Hq.sel = stateIdx("PA"); Hq.tab = 0;
                shot(u, dir, "ui_gen_hq");
                Hq.mapMode = MM_MONEY; Hq.tab = 1; shot(u, dir, "ui_gen_money");
                Hq.mapMode = MM_FIELD; Hq.tab = 2; shot(u, dir, "ui_gen_field");
                Hq.mapMode = MM_INTERNAL; Hq.tab = 4; shot(u, dir, "ui_gen_polls");
                Hq.tab = 5; shot(u, dir, "ui_gen_campaign");
                Debate gd = null;
                for (Debate dd : g2.debates) if (!dd.done && dd.party < 0 && !dd.vp) { gd = dd; break; }
                if (gd != null) { g2.week = gd.week; DebateView.start(u, gd); DebateView.choose(u, 0); for (int i = 0; i < 4; i++) DebateView.nextLine(u); shot(u, dir, "ui_gen_debate"); }
                while (g2.week < DAILY_START && g2.phase != PH_OVER) { g2.pending.clear(); for (Debate dd : Debates.thisWeek(g2)) if (!dd.done) { DebateRun r = Debates.start(g2, dd); r.myIdx = -1; while (!r.over) Debates.segment(g2, r, -1); } Week.resolve(g2); }
                g2.pending.clear();
                u.go(SC_HQ); u.modal = null;
                Deals.closing(u, g2); shot(u, dir, "ui_closing");
                u.modal = null;
                Closing.choose(g2, g2.me(), 1);
                for (int dd = 0; dd < 3; dd++) { g2.pending.clear(); Hq.preDay = g2.day; Week.resolve(g2); }
                Hq.makeDigest(u, g2, g2.me(), g2.week); Hq.digestT = u.tsec;
                Hq.tab = 0; Hq.mapMode = MM_PUBLIC; Hq.sel = -1; shot(u, dir, "ui_daily");
                Hq.digest = null;
                while (g2.phase != PH_OVER) { g2.pending.clear(); for (Debate dd : Debates.thisWeek(g2)) if (!dd.done) { DebateRun r = Debates.start(g2, dd); r.myIdx = -1; while (!r.over) Debates.segment(g2, r, -1); } Week.resolve(g2); }
                Night.start(u);
                for (int i = 0; i < 40; i++) Night.update(u, 0.25);
                shot(u, dir, "ui_night1");
                for (int i = 0; i < 200; i++) Night.update(u, 0.25);
                Night.finaleT = 3;
                shot(u, dir, "ui_night2");
                u.go(SC_END); shot(u, dir, "ui_end");
                Results.open(u);
                for (int t = 0; t < 5; t++) { Results.tab = t; shot(u, dir, "ui_results" + t); }
                Results.tab = 0; Results.mapView = 1; shot(u, dir, "ui_results_swing");
                Results.mapView = 2; shot(u, dir, "ui_results_turnout");
                Results.mapView = 0; Results.sel = stateIdx("PA"); shot(u, dir, "ui_results_pa");
                Results.sel = -1;
            }
            if (all || which.contains("ind")) {
                Settings s3 = new Settings();
                s3.mode = MODE_GENERAL; s3.party = IND; s3.seed = 11; s3.scenario = SC_THIRD;
                Arrays.fill(s3.staff, true);
                u.setup = s3;
                Screens.startGame(u, testIndependent());
                shot(u, dir, "ui_ind_welcome");
                u.modal = null;
                Screens.vpSel = -1; u.go(SC_VP); shot(u, dir, "ui_ind_vp"); u.go(SC_HQ);
                Game g3 = u.game;
                for (int w = 0; w < 6; w++) { g3.pending.clear(); for (Debate dd : Debates.thisWeek(g3)) if (!dd.done) { DebateRun r = Debates.start(g3, dd); r.myIdx = -1; while (!r.over) Debates.segment(g3, r, -1); } Week.resolve(g3); }
                g3.pending.clear();
                u.go(SC_HQ); u.modal = null;
                Hq.mapMode = MM_PUBLIC; Hq.tab = 4; Hq.sel = stateIdx("OH"); shot(u, dir, "ui_ind_hq");
                Hq.sel = -1;
                // the long campaign: ballot access
                Settings s4 = new Settings();
                s4.mode = MODE_FULL; s4.party = IND; s4.seed = 12; s4.scenario = SC_THIRD;
                Arrays.fill(s4.staff, true);
                s4.candId = "custom"; s4.custom = testIndependent();
                Game g4 = Setup.newGame(s4);
                Setup.prestart(g4);
                u.game = g4;
                Hq.begin(u);
                shot(u, dir, "ui_ind_long_welcome");
                u.modal = null;
                for (int w = 0; w < 6; w++) { g4.pending.clear(); Week.resolve(g4); }
                g4.pending.clear();
                u.go(SC_HQ);
                Hq.mapMode = MM_BALLOT; Hq.tab = 2; shot(u, dir, "ui_ind_ballot");
            }
        }

        static void portraits(String out) {
            int cols = 10, cw = 150, ch = 200;
            int n = ROSTER.size();
            int rows = (n + cols - 1) / cols;
            BufferedImage img = new BufferedImage(cols * cw + 20, rows * (ch + 30) + 20, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            Portrait.hints(g);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(C_BG);
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            for (int i = 0; i < n; i++) {
                Cand c = ROSTER.get(i);
                int x = 10 + (i % cols) * cw, y = 10 + (i / cols) * (ch + 30);
                Portrait.draw(g, c, x + 5, y, cw - 10, ch - 10, 0.7, 0, false, true);
                g.setColor(Color.white);
                g.setFont(F_XS);
                g.drawString(c.name(), x + 8, y + ch + 8);
            }
            g.dispose();
            try { javax.imageio.ImageIO.write(img, "png", new File(out)); } catch (IOException e) { e.printStackTrace(); }
            System.out.println("wrote " + out);
        }

        /** A created independent for tests: a business outsider in the centre. */
        static Cand testIndependent() {
            Cand c = new Cand();
            c.id = "custom"; c.custom = true; c.first = "Alex"; c.last = "Morgan"; c.party = IND; c.role = "Businessperson";
            c.homeAb = "OH"; c.home = stateIdx("OH"); c.born = 1970; c.faction = F_AMP;
            c.look = Look.parse("M 1 2 2 0.1 0 0 1.0 0.5 0 3 0.45 0.65");
            String[] t = Creator.ARCH_ATTR[3].split(" ");
            for (int i = 0; i < NA; i++) c.a[i] = Integer.parseInt(t[i]);
            c.econ = FACTIONS[c.faction].econ; c.cult = FACTIONS[c.faction].cult; c.plat = FACTIONS[c.faction].pos.clone();
            c.nameId = 0.35; c.poll = 1; c.cash = 10e6; c.list = 0.5e6; c.selfMax = 600e6;
            c.apG = new int[0]; c.apV = new double[0]; c.record = new String[0]; c.recIssue = new int[0]; c.recCred = new double[0];
            return c;
        }

        static void game(long seed, int mode, String cand, int scen, boolean verbose) {
            long t0 = System.currentTimeMillis();
            Settings set = new Settings();
            boolean ind = cand.equals("ind");
            set.mode = mode; set.candId = ind ? "custom" : cand; set.scenario = scen; set.seed = seed;
            if (ind) set.custom = testIndependent();
            set.party = ind ? IND : candById(cand).party;
            Arrays.fill(set.staff, true);
            Game g = Setup.newGame(set);
            if (ind && mode == MODE_FULL) Setup.prestart(g);
            if (aiOnly) { for (Camp k : g.camps) k.human = false; g.player = -1; }
            if (mode == MODE_FULL) {
                double[] d = Vote.primaryNational(g, DEM), r = Vote.primaryNational(g, REP);
                StringBuilder b = new StringBuilder("opening primary polls: ");
                for (Camp k : g.camps) if (k.party <= REP) b.append(k.last()).append(' ').append(Math.round((k.party == DEM ? d : r)[k.idx] * 100)).append("  ");
                System.out.println(b);
            }
            int lastNews = 0;
            while (g.phase != PH_OVER && g.week <= ELECTION_WEEK) {
                if (primOnly && g.phase >= PH_CONVENTION) break;
                autoPending(g);
                for (Debate d : Debates.thisWeek(g)) {
                    DebateRun run = Debates.start(g, d);
                    while (!run.over) Debates.segment(g, run, run.myIdx >= 0 ? Debates.aiTactic(g, run, run.myIdx) : -1);
                }
                Week.resolve(g);
                if (verbose) for (int i = lastNews; i < g.news.size(); i++) { News n = g.news.get(i); if (n.kind == NK_BIG || n.kind == NK_RESULT) System.out.println("  wk" + n.week + " " + n.text); }
                lastNews = g.news.size();
                if (verbose && (g.week == 40 || g.week == 62 || g.week == 80 || g.week == 96 || g.week == 101 || g.week == 104)) diag(g);
                if (verbose && g.phase >= PH_CONVENTION && g.week % 4 == 0) decomp(g);
                if (g.week % 8 == 0 && verbose) {
                    StringBuilder pb = new StringBuilder("  [wk " + g.week + " " + PHASE[g.phase] + "] approval " + fmt1(g.approval) + " env " + fmt1(g.env) + " unemp " + fmt1(g.unemp));
                    if (g.primaryPoll != null && g.phase <= PH_PRIMARY) for (int p = DEM; p <= REP; p++) {
                        pb.append(p == DEM ? "\n     D: " : "\n     R: ");
                        for (Camp k : g.party(p)) pb.append(k.last()).append(' ').append(Math.round(g.primaryPoll[k.idx] * 100)).append(k.announced ? "" : "?").append(" (").append(money(k.cash)).append(")  ");
                    }
                    if (g.phase >= PH_CONVENTION) pb.append("  nat ").append(margin(Polls.trueMargin(g, -1))).append(" pub ").append(margin(g.natPubAvg));
                    System.out.println(pb);
                }
            }
            autoPending(g);
            Camp d = g.nominee(DEM), r = g.nominee(REP);
            System.out.println("=== seed " + seed + " " + SCEN_NAME[scen] + "  D: " + (d != null ? d.name() : "-") + "  R: " + (r != null ? r.name() : "-")
                    + "  EV " + g.evD + "-" + g.evR + (g.evI > 0 ? "-I" + g.evI : "") + "  pop D " + fmt1(g.popD / Math.max(1, g.popD + g.popR + g.popO) * 100) + " R " + fmt1(g.popR / Math.max(1, g.popD + g.popR + g.popO) * 100)
                    + " O " + fmt1(g.popO / Math.max(1, g.popD + g.popR + g.popO) * 100) + (g.popI > 0 ? " (I " + fmt1(g.popI / Math.max(1, g.popD + g.popR + g.popO) * 100) + ")" : "")
                    + (g.contingent >= 0 ? " HOUSE PICKS " + PARTY[g.contingent] : "") + "  House D " + g.houseSeatsD + "  [" + (System.currentTimeMillis() - t0) + " ms]");
            if (ind) { Camp k = g.indep(); if (k != null) System.out.println("  independent: ballots " + Ballot.count(k) + " (" + Ballot.evOnBallot(k) + " EV), raised " + money(k.raised) + ", name ID " + pct0(k.nameId) + ", final poll " + fmt1(g.natPubI)); }
            if (g.result != null) {
                StringBuilder sb = new StringBuilder("  ");
                for (String ab : new String[]{"PA", "MI", "WI", "GA", "AZ", "NV", "NC", "NH", "MN", "VA", "FL", "TX", "OH", "IA", "NE", "ME"}) {
                    int s = stateIdx(ab);
                    sb.append(ab).append(' ').append(margin(Vote.margin(g.result[s]))).append("  ");
                }
                System.out.println(sb);
                StringBuilder pb = new StringBuilder("  public: ");
                for (String ab : new String[]{"PA", "MI", "WI", "GA", "AZ", "NV", "NC", "NH", "MN", "VA", "FL", "TX", "OH", "IA", "CO", "NM", "NJ", "NY"}) {
                    int s = stateIdx(ab);
                    pb.append(ab).append(' ').append(margin(g.pubAvg[s])).append('(').append((int) g.pubN[s]).append(")  ");
                }
                System.out.println(pb);
                if (d != null) System.out.println("  D raised " + money(d.raised) + " spent " + money(d.spent) + " doors " + compact(sum(d.doorsT)) + "   R raised " + money(r.raised) + " spent " + money(r.spent) + " doors " + compact(sum(r.doorsT)));
            }
        }

        static void diag(Game g) {
            System.out.println("  --- diag wk " + g.week + " " + PHASE[g.phase]);
            for (Camp k : g.camps) {
                if (!k.active || k.party > REP) continue;
                if (g.phase >= PH_CONVENTION && !k.nominee) continue;
                if (g.phase < PH_CONVENTION && (g.primaryPoll == null || g.primaryPoll[k.idx] < 0.12)) continue;
                StringBuilder b = new StringBuilder("   " + k.last() + " cash " + money(k.cash) + " raised " + money(k.raised) + " spent " + money(k.spent) + " [");
                for (int i = 0; i < RS_N; i++) b.append(money(k.raisedBy[i])).append(i < RS_N - 1 ? " " : "]");
                b.append(" lastwk +").append(money(k.raisedHist[Math.min(109, g.week - 1)])).append(" -").append(money(k.spentHist[Math.min(109, g.week - 1)]));
                System.out.println(b);
                double org = 0, off = 0, vol = 0, sh = 0, dr = 0;
                for (int s = 0; s < NS; s++) { org += k.organizers[s]; off += k.offices[s]; vol += k.volunteers[s]; sh += k.shiftsW[s]; dr += k.doorsW[s]; }
                StringBuilder f = new StringBuilder("     org " + fmt0(org) + " offices " + fmt0(off) + " vols " + compact(vol) + " shifts/wk " + compact(sh) + " doors/wk " + compact(dr) + " doorsT " + compact(sum(k.doorsT)) + " list " + compact(k.smallList) + "  hq " + k.hq + " enth " + fmt1(k.enthusiasm * 100));
                System.out.println(f);
                StringBuilder t = new StringBuilder("     ");
                for (String ab : new String[]{"PA", "MI", "WI", "GA", "NH", "IA", "NV", "AZ", "SC"}) {
                    int s = stateIdx(ab);
                    t.append(ab).append(": org ").append(fmt0(k.organizers[s])).append(" v ").append(compact(k.volunteers[s])).append(" d ").append(compact(k.doorsW[s]))
                            .append(" tv ").append(fmt0(k.tvStock[s])).append(" pac ").append(fmt0(k.superPac[s])).append(" dg ").append(fmt0(k.digStock[s])).append(" vis ").append(fmt1(k.visit[s]))
                            .append(" per ").append(String.format("%.3f", k.persuade[s])).append(" li ").append(String.format("%.3f", k.localImage[s])).append(" camp ").append(String.format("%.3f", Vote.campTerm(k, s, 20))).append(" | ");
                    if (ab.equals("GA")) t.append("\n     ");
                }
                System.out.println(t);
            }
        }

        static void decomp(Game g) {
            for (int p = DEM; p <= REP; p++) {
                Camp k = g.nominee(p);
                if (k == null) continue;
                double q = Vote.quality(g, k), is = 0, ap = 0, im = 0, vp = 0, cp = 0, w = 0;
                Cand v = g.vpOf(p);
                for (int s = 0; s < NS; s++) for (int c = 0; c < NCELL; c++) {
                    double n = Elect.cvap[s][c] * Elect.turnB[s][c] * Elect.elast[s][c];
                    is += n * Vote.issueTerm(g, k, s, c); ap += n * Vote.K_APPEAL * Vote.groupTerm(k.c.apG, k.c.apV, s, c); im += n * Vote.groupTerm(k.imageG, s, c);
                    if (v != null) vp += n * Vote.vpTerm(g, v, s, c);
                    cp += n * Vote.campTerm(k, s, c);
                    w += n;
                }
                double att = Vote.attackDamage(k) / 0.05;
                System.out.println(String.format("   %-14s qual %+.3f issue %+.3f appeal %+.3f image %+.3f favor %+.3f flip %.3f scandal %.3f attacks %.3f vp %+.3f camp %+.3f  enth %.2f  plat-shift %.2f",
                        k.last(), q, is / w, ap / w, im / w, k.favor, k.flipflop * (1 - k.a(A_AUT) / 140.0) * 0.06, k.scandalRevealed * 0.10, att * 0.05, vp / w, cp / w, k.enthusiasm, platShift(k)));
                StringBuilder b = new StringBuilder("     attacks:");
                for (int a = 1; a < ATTACK.length; a++) if (k.attackStick[a] > 0.05) b.append(' ').append(ATTACK[a]).append('=').append(fmt1(k.attackStick[a]));
                System.out.println(b);
            }
        }

        static double platShift(Camp k) { double d = 0; for (int i = 0; i < NI; i++) d += Math.abs(k.plat[i] - k.c.plat[i]); return d; }

        static double sum(double[] a) { double t = 0; for (double v : a) t += v; return t; }

        static void autoPending(Game g) {
            while (!g.pending.isEmpty()) {
                Object[] p = g.pending.remove(0);
                int ev = (Integer) p[0];
                Camp me = g.me();
                if (me == null) continue;
                if (ev == EV_VP) { g.setVp(me.party, Primary.aiPickVp(g, me)); continue; }
                if (ev == EV_CONCEDE) continue;
                if (ev == EV_DEBATE_DEAL) continue;
                Events.decide(g, me, ev, AI.choose(g, me, ev, ((String[]) p[3]).length));
            }
        }
    }
}
