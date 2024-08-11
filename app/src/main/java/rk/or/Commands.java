package rk.or;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import rk.or.android.MainActivity;
import rk.or.android.View3D;

/**
 * Commands are interpreted here
 * deals with animation, undo, pause with a state machine
 */
public class Commands {
    // References
    public Model model;
    public View3D view3d;
    // States
    public enum State {idle, run, anim, pause, undo}
    public State state = State.idle;
    // Serialized model to undo
    private final LinkedList<byte[]> undo;
    // Done Commands
    private final LinkedList<String> done;
    private boolean undoInProgress = false;
    // Folding command and index in command[] and no scanned
    private String[] todo;
    private int iTok, p, iBeginAnim;
    // tstart and duration give tn = (t-tstart-pauseDuration)/duration from 0 to 100%
    private long tstart, duration, pauseStart, pauseDuration = 0;
    // Time interpolated at instant p preceding and at instant n current
    private float tni = 1, tpi = 0;
    // scale, cx, cy, cz used in ZoomFit
    private float[] za = {0, 0, 0, 0};
    // Interpolator used in anim() to map tn (time normalized) to tni (time interpolated)
    private Interpolator interpolator = new LinearInterpolator();
    // Angle used for fold as a starting value when animation starts
    private float angleBefore;
    // Coefficient to multiply value given in Offset commands
    private float kOffset = 0.2f; // 0.2f for real rendering
    /**
     * Constructor initialize linked list
     */
    public Commands(MainActivity activity, Model model, View3D view3d) {
        undo = new LinkedList<byte[]>();
        done = new LinkedList<String>();
        state = State.idle;
    }

    /**
     * Main entry point on state machine
     */
    public synchronized void command(String cde) {

// -- State Idle tokenize list of command
        if (state == State.idle) {
            if (cde.equals("u")) {
                Collections.reverse(done);
                todo = done.toArray(new String[0]);
                Collections.reverse(done);
                undo(); // We are exploring todo[]
                return;
            } else if (cde.startsWith("read")) {
                String filename = cde.substring(5);
                cde = read(filename.trim());
                done.clear();
                undo.clear();
                // Continue to Execute
            } else if (cde.equals("co") || cde.equals("pa")) {
                // In idle, no job, continue, or pause are irrelevant
                return;
            } else if (cde.startsWith("d")) {
                // Starts a new folding
                done.clear();
                undo.clear();
            }
            // Execute
            todo = tokenize(cde);
            state = State.run;
            iTok = 0;
            commandLoop();
            return;
        }
// -- State Run execute list of command
        if (state == State.run) {
            commandLoop();
            return;
        }
// -- State Animation execute up to ')' or pause
        if (state == State.anim) {
            // "Pause"
            if (cde.equals("pa")) {
                state = State.pause;
            }
            return;
        }
// -- State Paused in animation
        if (state == State.pause) {
            // "Continue"
            if (cde.equals("co")) {
                pauseDuration = System.currentTimeMillis() - pauseStart;
                // Continue animation
                state = State.anim;
                view3d.requestRender();
            } else if (cde.equals("u")) {
                // Undo one step
                state = State.undo;
                undo();
            }
            return;
        }
// -- State undo
        if (state == State.undo) {
            if (!undoInProgress) {
                if (cde.equals("u")) {
                    // Ok continue to undo
                    undo();
                } else if (cde.equals("co")) {
                    // Switch back to run
                    state = State.run;
                    commandLoop();
                } else if (cde.equals("pa")) {
                    // Forbidden ignore pause
                }
            }
        }
    }

    /**
     * Loop to execute commands
     */
    private void commandLoop() {
        while (iTok < todo.length) {
            // Breaks loop to launch animation on 't'
            if (todo[iTok].equals("t")) {
                // Mark
                pushUndo();
                // Time t duration ... )
                done.addFirst(todo[iTok++]);
                // iTok will be incremented by duration = get()
                done.addFirst(todo[iTok]);
                duration = (long) get();
                pauseDuration = 0;
                state = State.anim;
                animStart();
                // Return breaks the loop, giving control to anim
                return;
            } else if (todo[iTok].equals(")")) {
                // Finish pushing command
                done.addFirst(todo[iTok++]);
                continue;
            }
            int iBefore = iTok;

            // Execute one command
            int iReached = execute();

            // Push modified model
            pushUndo();
            // Add done commands to done list
            while (iBefore < iReached) {
                done.addFirst(todo[iBefore++]);
            }
            view3d.requestRender();
        }
        // End of command line switch to idle
        if (state == State.run) {
            state = State.idle;
        }
    }

    /**
     * Tells view3d to requestRender()
     */
    private void animStart() {
        tstart = System.currentTimeMillis();
        // After each onDrawFrame() View3D calls anim() and if true calls requestRender()
        tpi = 0.0f;
        // Launch animation
        view3d.requestRender();
    }

    /**
     * Called from View3D at each redraw
     * return true if anim should continue false if anim should end
     */
    public boolean anim() {
        if (state == State.undo) {
            int index = popUndo();
            boolean ret = index > iTok;
            // Stop undo if undo mark reached and switch to repaint
            if (ret == false) {
                undoInProgress = false;
                view3d.requestRender();
            }
            return ret;
        } else if (state == State.pause) {
            pauseStart = System.currentTimeMillis();
            return false;
        } else if (state != State.anim) {
            return false;
        }
        long t = System.currentTimeMillis();
        // Compute tn varying from 0 to 1
        float tn = (t - tstart - pauseDuration) / (float) duration; // tn from 0 to 1
        if (tn > 1.0f)
            tn = 1.0f;
        tni = interpolator.interpolate(tn);

        // Execute commands just after t xxx up to including ')'
        iBeginAnim = iTok;
        while (!todo[iTok].equals(")")) {
            execute();
        }
        // For undoing animation
        // We are only interested in model, not in command
        pushUndo();

        // Keep t preceding tn
        tpi = tni; // t preceding

        // If Animation will finish, set end values
        if (tn >= 1.0f) {
            tni = 1.0f;
            tpi = 0.0f;
            // Push done
            while (iBeginAnim < iTok) {
                // Time t duration ... )
                done.addFirst(todo[iBeginAnim++]);
            }
            // Switch back to run and launch next cde
            state = State.run;
            commandLoop();
            // If commandLoop has launched another animation we continue
            return state == State.anim;
            // OK we stop anim
        }
        // Rewind to continue animation
        iTok = iBeginAnim;
        return true;
    }

    /**
     * Undo
     */
    private void undo() {
        if (undo.size() == 0) {
            return;
        }
        // We should be Only in states : idle pause undo
        // Rewind to last 't' or 'd' command from done
        if (state == State.idle) {
            iTok = todo.length - 1;
        }
        while (iTok >= 0) {
            iTok--;
            done.poll();
            String tok = todo[iTok];
            // Undo Mark, or beginning Define
            if (tok.equals("d") || tok.equals("t"))
                break;
        }
        // We have rewound to 't' or 'd', launch the sequence to undo to iTok
        tni = 1;
        tpi = 0;
        state = State.undo;
        undoInProgress = true;
        // Launch animation to popUndo until iTok reached
        view3d.requestRender();
    }

    /**
     * Push undo
     */
    private void pushUndo() {
        undo.addFirst(model.getSerialized());
        undo.addFirst(getSerialized(done.size()));
    }

    /**
     * Serial encoder for index in todo string.
     * Returns byte[] array with one Integer for the int parameter
     */
    private byte[] getSerialized(int i) {
        ByteArrayOutputStream bs = new ByteArrayOutputStream();
        ObjectOutputStream oos;
        try {
            oos = new ObjectOutputStream(bs);
            oos.writeObject(Integer.valueOf(i)); // Yes we need Integer
            oos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bs.toByteArray();
    }

    /**
     * Serial decoder for index
     * Returns int from the byte[] array  parameter
     */
    private int deserialize(byte[] buf) {
        Integer ret = 0;
        ByteArrayInputStream bais = new ByteArrayInputStream(buf);
        ObjectInputStream dec;
        try {
            dec = new ObjectInputStream(bais);
            ret = (Integer) dec.readObject();
            dec.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ret.intValue();
    }

    /**
     * Pop undo index, model and return index
     */
    private int popUndo() {
        byte[] index = undo.poll();
        byte[] model = undo.poll();
        if (index == null) return 0;
        ByteArrayInputStream bais = new ByteArrayInputStream(model);
        ObjectInputStream dec;
        try {
            dec = new ObjectInputStream(bais);
            this.model = (Model) dec.readObject();
            dec.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return deserialize(index);
    }

    /**
     * Execute one command token on model
     */
    private int execute() {
        // Work on this model
//    Model model = this.model;
        // Commands
        if (todo[iTok].equals("d")) { // "d : define"
            // Define sheet by 4 points x,y CCW
            iTok++;
            model.init(get(), get(), get(), get(), get(), get(), get(), get());
        }
        // Origami splits
        else if (todo[iTok].equals("b")) { // "b : by"
            // Split by two points all (or listed) faces
            iTok++;
            Point a = model.points.get((int) get());
            Point b = model.points.get((int) get());
            model.splitBy(a, b, listFaces(model));
        } else if (todo[iTok].equals("c")) { // "c : cross"
            // Split across two points all (or just listed) faces
            iTok++;
            Point a = model.points.get((int) get());
            Point b = model.points.get((int) get());
            model.splitAcross(a, b, listFaces(model));
        } else if (todo[iTok].equals("p")) { // "p : perpendicular"
            // Split perpendicular of line by point all (or listed) faces
            iTok++;
            Segment s = model.segments.get((int) get());
            Point p = model.points.get((int) get());
            model.splitOrtho(s, p, listFaces(model));
        } else if (todo[iTok].equals("lol")) { // "lol : LineOnLine"
            // Split by a plane passing between segments all (or listed) faces
            iTok++;
            Segment s0 = model.segments.get((int) get());
            Segment s1 = model.segments.get((int) get());
            model.splitLineToLine(s0, s1, listFaces(model));
        }
        // Segments splits
        else if (todo[iTok].equals("s")) { // "s : split segment numerator denominator"
            // Split segment by N/D
            iTok++;
            Segment s = model.segments.get((int) get());
            float n = get();
            float d = get();
            model.splitSegment(s, n / d);
        } else if (todo[iTok].equals("sc")) { // "sc : split segment crossing"
            // Split segment where they cross
            iTok++;
            Segment s1 = model.segments.get((int) get());
            Segment s2 = model.segments.get((int) get());
            model.splitSegmentCrossing(s1, s2);
        }
        // Animation commands use tni tpi
        else if (todo[iTok].equals("r")) { // " r : rotate"
            // Rotate Seg Angle Points with animation
            iTok++;
            Segment s = model.segments.get((int) get());
            float angle = get() * (tni - tpi);
            model.rotate(s, angle, listPoints(model));
        } else if (todo[iTok].equals("f")) { // "f : fold to angle"
            iTok++;
            Segment s = model.segments.get((int) get());
            // Cache current angle at start of animation
            // TODO accept multiple folds, multiples angles in one animation
            if (tpi == 0)
                angleBefore = model.computeAngle(s);
            float angle = (get() - angleBefore) * (tni - tpi);
            List<Point> list = listPoints(model);
            // Reverse segment to have the first point on left face
            if (tpi == 0 && model.faceRight(s.p1, s.p2).points.contains(list.get(0)))
                s.reverse();
            model.rotate(s, angle, list);
        }
        // Adjust points
        else if (todo[iTok].equals("a")) { // "a : adjust"
            // Adjust Points in 3D to fit 3D length
            iTok++;
            model.adjust(listPoints(model));
        }
        // Adjust point with only given segments
        else if (todo[iTok].equals("as")) { // "as : adjust point segments"
            // Adjust Points in 3D to fit 3D length
            iTok++;
            Point p0 = model.points.get((int) get());
            model.adjustSegments(p0, listSegments(model));
        } else if (todo[iTok].equals("flat")) { // "flat : z = 0"
            // Move all point to z = 0
            iTok++;
            model.flat(listPoints(model));
        }
        // Offsets
        else if (todo[iTok].equals("o")) { // "o : offset"
            // Offset by dz the list of faces : o dz f1 f2...
            iTok++;
            float dz = get() * kOffset;
            model.offset(dz, listFaces(model));
        } else if (todo[iTok].equals("od")) { // "od : offset decal"
            // Get the maximal offset of all listed faces add 1
            // and subtract for all listed faces (or all if none listed)
            iTok++;
            float dz = get() * kOffset;
            model.offsetDecal(dz, listFaces(model));
        } else if (todo[iTok].equals("oa")) { // "oa : offsetAdd"
            // Add Offset dz to the list of faces : oa dz f1 f2...
            iTok++;
            float dz = get() * kOffset;
            model.offsetAdd(dz, listFaces(model));
        } else if (todo[iTok].equals("om")) { // "om : offsetMul"
            // Multiply Offset by k for all faces : om k
            iTok++;
            float k = get();
            model.offsetMul(k, listFaces(model));
        } else if (todo[iTok].equals("ob")) { // "ob : offsetBetween"
            iTok++;
            model.offsetBetween(listFaces(model));
        }
        // Moves
        else if (todo[iTok].equals("m")) { // "m : move dx dy dz pts"
            // Move 1 Point in 3D with Coefficient for animation
            iTok++;
            model.move(get() * (tni - tpi), get() * (tni - tpi), get() * (tni - tpi), listPoints(model));
        } else if (todo[iTok].equals("mo")) { // "mo : move on"
            // Move all points on one with animation
            iTok++;
            Point p0 = model.points.get((int) get());
            float k2 = (1 - tni) / (1 - tpi);
            float k1 = tni - tpi * k2;
            model.moveOn(p0, k1, k2, listPoints(model));
        } else if (todo[iTok].equals("mol")) { // "mol : move on line"
            // Move all points on line with animation
            iTok++;
            Segment p0 = model.segments.get((int) get());
            float k2 = (1 - tni) / (1 - tpi);
            float k1 = tni - tpi * k2;
            model.moveOnLine(p0, k1, k2, listPoints(model));
        } else if (todo[iTok].equals("stp")) { // "stp : stick on point"
            // Move all points on one no animation
            iTok++;
            Point p0 = model.points.get((int) get());
            model.moveOn(p0, 1, 0, listPoints(model));
        } else if (todo[iTok].equals("stl")) { // "stl : stick on line"
            // Move all points on line with animation
            iTok++;
            Segment p0 = model.segments.get((int) get());
            model.moveOnLine(p0, 1, 0, listPoints(model));
        }
        // Turns
        else if (todo[iTok].equals("tx")) { // "tx : TurnX"
            iTok++;
            model.turn(get() * (tni - tpi), 1);
        } else if (todo[iTok].equals("ty")) { // "ty : TurnY"
            iTok++;
            model.turn(get() * (tni - tpi), 2);
        } else if (todo[iTok].equals("tz")) { // "tz : TurnZ"
            iTok++;
            model.turn(get() * (tni - tpi), 3);
        }
        // Zooms
        else if (todo[iTok].equals("z")) { // "z : Zoom scale,x,y"
            iTok++;
            float scale = get(), x = get(), y = get();
            // for animation
            float ascale = (1 + tni * (scale - 1)) / (1 + tpi * (scale - 1));
            float bfactor = scale * (tni / ascale - tpi);
            model.move(x * bfactor, y * bfactor, 0, null);
            model.scaleModel(ascale);
        } else if (todo[iTok].equals("zf")) { // "zf : Zoom Fit"
            iTok++;
            if (tpi == 0) {
                float[] b = model.get3DBounds();
                float w = 400;
                za[0] = w / Math.max(b[2] - b[0], b[3] - b[1]);
                za[1] = -(b[0] + b[2]) / 2;
                za[2] = -(b[1] + b[3]) / 2;
            }
            float scale = (1 + tni * (za[0] - 1)) / (1 + tpi * (za[0] - 1));
            float bfactor = za[0] * (tni / scale - tpi);
            model.move(za[1] * bfactor, za[2] * bfactor, 0, null);
            model.scaleModel(scale);
        }
        // Interpolators
        else if (todo[iTok].equals("il")) { // "il : Interpolator Linear"
            iTok++;
            interpolator = new LinearInterpolator();
        } else if (todo[iTok].equals("ib")) { // "ib : Interpolator Bounce"
            iTok++;
            interpolator = new BounceInterpolator();
        } else if (todo[iTok].equals("io")) { // "io : Interpolator OverShoot"
            iTok++;
            interpolator = new OvershootInterpolator(3);
        } else if (todo[iTok].equals("ia")) { // "ia : Interpolator Anticipate"
            iTok++;
            interpolator = new AnticipateInterpolator(3);
        } else if (todo[iTok].equals("iao")) { // "iao : Interpolator Anticipate OverShoot"
            iTok++;
            interpolator = new AnticipateOvershootInterpolator(3);
        } else if (todo[iTok].equals("iad")) { // "iad : Interpolator Accelerate Decelerate"
            iTok++;
            interpolator = new AccelerateDecelerateInterpolator();
        } else if (todo[iTok].equals("iso")) { // "iso Interpolator Spring Overshoot"
            iTok++;
            interpolator = new SpringOvershootInterpolator();
        } else if (todo[iTok].equals("isb")) { // "isb Interpolator Spring Bounce"
            iTok++;
            interpolator = new SpringBounceInterpolator();
        } else if (todo[iTok].equals("igb")) { // "igb : Interpolator Gravity Bounce"
            iTok++;
            interpolator = new GravityBounceInterpolator();
        }
        // Mark points and segments
        else if (todo[iTok].equals("pt")) { // "select points"
            iTok++;
            model.selectPts(listPoints(model));
        } else if (todo[iTok].equals("seg")) { // "select segments"
            iTok++;
            model.selectSegs(listSegments(model));
        }
        // Fall through
        else if (todo[iTok].equals("t") || todo[iTok].equals(")")
                || todo[iTok].equals("u") || todo[iTok].equals("co")
                || todo[iTok].equals("end")) {
            iTok++;
            return -1;
        } else {
            // ignore dangling token
            iTok++;
        }
        return iTok;
    }

    /**
     * Make a list from following points numbers
     */
    private List<Point> listPoints(Model model) {
        List<Point> list = new LinkedList<Point>();
        while (!Float.isNaN(get())) try {
            list.add(model.points.get(p));
        } catch (Exception e) {
            System.out.println("Ignore Point:" + p);
        }
        return list;
    }

    /**
     * Make a list from following segments numbers
     */
    private List<Segment> listSegments(Model model) {
        List<Segment> list = new LinkedList<Segment>();
        while (!Float.isNaN(get()))
            try {
                list.add(model.segments.get(p));
            } catch (Exception e) {
                System.out.println("Ignore Segment:" + p);
            }
        return list;
    }

    /**
     * Make a list from following faces numbers
     */
    private List<Face> listFaces(Model model) {
        List<Face> list = new LinkedList<Face>();
        while (!Float.isNaN(get()))
            list.add(model.faces.get(p));
        return list;
    }

    /**
     * Tokenize, split the String in Array of String
     */
    private String[] tokenize(String input) {
        ArrayList<String> matchList = new ArrayList<String>();
        StringBuilder sb = new StringBuilder();
        boolean lineComment = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == ' ' || c == '\r' || c == '\n') {
                // keep token before space or end of line
                if (sb.length() != 0)
                    matchList.add(sb.toString());
                if (sb.toString().equals("end"))
                    break;
                // done with this token if any, rewind StringBuffer
                sb.delete(0, sb.length());
                lineComment = false;
            } else if (c == ')') {
                // keep string before parent
                if (sb.length() != 0)
                    matchList.add(sb.toString());
                // add parent
                matchList.add(")");
                // done with this two token
                sb.delete(0, sb.length());
            } else if (c == '/') {
                // Skip to the end of line,
                for (; input.charAt(i) != '\n' && i < input.length() - 1; i++) ;
                lineComment = true;
            } else {
                // keep character to form the token
                sb.append(c);
            }
        }
        // Take care of input ending with a token
        char c = input.charAt(input.length() - 1);
        if (c != ' ' && c != '\r' && c != '\n' && c != ')' && !lineComment) {
            matchList.add(sb.toString());
        }
        // Construct result
        return matchList.toArray(new String[0]);
    }

    /**
     * Helper to get token. Returns float and set p as int
     */
    private float get() {
        try {
            float val = Float.parseFloat(todo[iTok]);
            iTok++;
            p = (int) val;
            return val;
        } catch (Exception e) {
            // s is not numeric
            return Float.NaN;
        }
    }

    /**
     * Read a File in a String
     */
    private String read(String name) {
        URL fileURL = Commands.class.getResource("/rk/or/raw/" + name);
        InputStream input;
        StringBuilder sb = new StringBuilder();
        try {
            // If the file is not in the jar, get it strait
            if (fileURL == null)
                fileURL = new File(name).toURI().toURL();
            // We do not use openRawResource() which depends on android
            // Open and read all characters in the StringBuffer
            input = fileURL.openStream();
            int car = 0;
            while ((car = input.read()) != -1) {
                sb.append((char) car);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Returns the string from the StringBuffer
        return sb.toString();
    }
}
