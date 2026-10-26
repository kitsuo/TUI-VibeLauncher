package ohi.andre.consolelauncher.managers;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.IBinder;
import android.text.InputType;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.options.Behavior;
import ohi.andre.consolelauncher.managers.xml.options.Theme;
import ohi.andre.consolelauncher.managers.xml.options.Ui;
import ohi.andre.consolelauncher.tuils.LongClickMovementMethod;
import ohi.andre.consolelauncher.tuils.LongClickableSpan;
import ohi.andre.consolelauncher.tuils.PrivateIOReceiver;
import ohi.andre.consolelauncher.tuils.ProcessInterrupter;
import ohi.andre.consolelauncher.tuils.Tuils;
import ohi.andre.consolelauncher.tuils.interfaces.CommandExecuter;

/*Copyright Francesco Andreuzzi

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.*/

public class TerminalManager {

    private final int SCROLL_DELAY = 200;
    private final int CMD_LIST_SIZE = 40;

    public static final int CATEGORY_INPUT = 10, CATEGORY_OUTPUT = 11, CATEGORY_NO_COLOR = 20;

    public static int NO_COLOR = Integer.MAX_VALUE;

    private long lastEnter;

    private String prefix;
    private String suPrefix;

    private ScrollView mScrollView;
    private TextView mTerminalView;
    private EditText mInputView;

    private TextView mPrefix;
    private boolean suMode;

    private List<String> cmdList = new ArrayList<>(CMD_LIST_SIZE);
    private int howBack = -1;

    private Runnable mScrollRunnable = new Runnable() {
        @Override
        public void run() {
            mScrollView.fullScroll(ScrollView.FOCUS_DOWN);
            mInputView.requestFocus();
        }
    };

    private MainPack mainPack;

    private boolean defaultHint = true;

    private int clearCmdsCount= 0;

    private int clearAfterCmds, clearAfterMs, maxLines;
    private Runnable clearRunnable = new Runnable() {

        @Override
        public void run() {
            clear();
            mTerminalView.postDelayed(this, clearAfterMs);
        }
    };

    private String inputFormat, outputFormat;
    private int inputColor, outputColor;

    private boolean clickCommands, longClickCommands;

    public Context mContext;

    private CommandExecuter executer;

    public TerminalManager(final TextView terminalView, EditText inputView, TextView prefixView, ImageView submitView, final ImageView backView, ImageButton nextView, ImageButton deleteView,
                           ImageButton pasteView, final Context context, MainPack mainPack, CommandExecuter executer) {
        if (terminalView == null || inputView == null || prefixView == null)
            throw new UnsupportedOperationException();

        this.mContext = context;

//        up/down arrows start from the persisted history, so it survives restarts
        if(HistoryManager.instance != null) {
            List<String> recent = HistoryManager.instance.recent(CMD_LIST_SIZE);
            for(int i = recent.size() - 1; i >= 0; i--) cmdList.add(recent.get(i));
        }
        this.executer = executer;

        this.mainPack = mainPack;

        this.clickCommands = XMLPrefsManager.getBoolean(Behavior.click_commands);
        this.longClickCommands = XMLPrefsManager.getBoolean(Behavior.long_click_commands);

        this.clearAfterMs = XMLPrefsManager.getInt(Behavior.clear_after_seconds) * 1000;
        this.clearAfterCmds = XMLPrefsManager.getInt(Behavior.clear_after_cmds);
        this.maxLines = XMLPrefsManager.getInt(Behavior.max_lines);

        this.typingAnimation = XMLPrefsManager.getBoolean(Ui.typing_animation);
        this.typingCharsPerFrame = Math.max(1, TYPING_FRAME_MS / Math.max(1, XMLPrefsManager.getInt(Ui.typing_speed)));

        inputFormat = XMLPrefsManager.get(Behavior.input_format);
        outputFormat = XMLPrefsManager.get(Behavior.output_format);

        inputColor = XMLPrefsManager.getColor(Theme.input_color);
        outputColor = XMLPrefsManager.getColor(Theme.output_color);

        prefix = XMLPrefsManager.get(Ui.input_prefix);
        suPrefix = XMLPrefsManager.get(Ui.input_root_prefix);

        int ioSize = XMLPrefsManager.getInt(Ui.input_output_size);

        prefixView.setTypeface(Tuils.getTypeface(context));
        prefixView.setTextColor(XMLPrefsManager.getColor(Theme.input_color));
        prefixView.setTextSize(ioSize);
        prefixView.setText(prefix.endsWith(Tuils.SPACE) ? prefix : prefix + Tuils.SPACE);
        this.mPrefix = prefixView;

        int toolbarColor = XMLPrefsManager.getColor(Theme.toolbar_color);
        int enterColor = XMLPrefsManager.getColor(Theme.enter_color);

        if (submitView != null) {
            submitView.setColorFilter(enterColor, android.graphics.PorterDuff.Mode.SRC_IN);
            submitView.setOnClickListener(v -> onNewInput());
        }

        if (backView != null) {
            backView.setColorFilter(toolbarColor, android.graphics.PorterDuff.Mode.SRC_IN);
            backView.setBackgroundColor(0);
            backView.setOnClickListener(v -> onBackPressed());
        }

        if (nextView != null) {
            nextView.setColorFilter(toolbarColor, android.graphics.PorterDuff.Mode.SRC_IN);
            nextView.setBackgroundColor(0);
            nextView.setOnClickListener(v -> onNextPressed());
        }

        if (pasteView != null) {
            pasteView.setColorFilter(toolbarColor, android.graphics.PorterDuff.Mode.SRC_IN);
            pasteView.setBackgroundColor(0);
            pasteView.setOnClickListener(v -> {
                String text = Tuils.getTextFromClipboard(context);
                if(text != null && text.length() > 0) {
                    setInput(getInput() + text);
                }
            });
        }

        if (deleteView != null) {
            deleteView.setColorFilter(toolbarColor, android.graphics.PorterDuff.Mode.SRC_IN);
            deleteView.setBackgroundColor(0);
            deleteView.setOnClickListener(v -> setInput(Tuils.EMPTYSTRING));
//            long press: Ctrl+C, stops the running command (ping, top...) or is sent to the ssh server
            deleteView.setOnLongClickListener(v -> {
                sendCtrlC();
                return true;
            });
        }

        this.mTerminalView = terminalView;
        this.mTerminalView.setTypeface(Tuils.getTypeface(context));
        this.mTerminalView.setTextSize(ioSize);
        this.mTerminalView.setFocusable(false);
        this.mTerminalView.setMovementMethod(LongClickMovementMethod.getInstance(XMLPrefsManager.getInt(Behavior.long_click_duration)));

        int hintColor = XMLPrefsManager.getColor(Theme.session_info_color);

        ColorStateList list = mTerminalView.getHintTextColors();
        try {
            Field colors = list.getClass().getDeclaredField("mColors");
            Field dColor = list.getClass().getDeclaredField("mDefaultColor");

            colors.setAccessible(true);
            dColor.setAccessible(true);

            int[] a = (int[]) colors.get(list);
            for(int c = 0; c < a.length; c++) {
                a[c] = hintColor;
            }

            colors.set(list, a);
            dColor.set(list, hintColor);
        } catch (Exception e) {
            Tuils.log(e);
        }

        if(clearAfterMs > 0) this.mTerminalView.postDelayed(clearRunnable, clearAfterMs);
        if(maxLines > 0) {
            this.mTerminalView.getViewTreeObserver().addOnPreDrawListener(() -> {
                if(TerminalManager.this.mTerminalView == null) return true;

                Layout l = terminalView.getLayout();
                if(l == null) return true;

                int count = l.getLineCount() - 1;

                if(count > maxLines) {
                    int excessive = count - maxLines;

                    CharSequence text = terminalView.getText();
                    while (excessive >= 0) {
                        int index = TextUtils.indexOf(text, Tuils.NEWLINE);
                        if(index == -1) break;
                        text = text.subSequence(index + 1, text.length());
                        excessive--;
                    }

                    terminalView.setText(text);
                }

                return true;
            });
        }

        View v = mTerminalView;
        do {
            v = (View) v.getParent();
        } while (!(v instanceof ScrollView));
        this.mScrollView = (ScrollView) v;

        this.mInputView = inputView;
        this.mInputView.setTextSize(ioSize);
        this.mInputView.setTextColor(XMLPrefsManager.getColor(Theme.input_color));
        this.mInputView.setTypeface(Tuils.getTypeface(context));
        this.mInputView.setHint(Tuils.getHint(mainPack.currentDirectory.getAbsolutePath()));
        this.mInputView.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        Tuils.setCursorDrawableColor(this.mInputView, XMLPrefsManager.getColor(Theme.cursor_color));
        this.mInputView.setHighlightColor(Color.TRANSPARENT);
        this.mInputView.setOnEditorActionListener((v1, actionId, event) -> {
            if(!mInputView.hasFocus()) mInputView.requestFocus();

//                physical enter
            if(actionId == KeyEvent.ACTION_DOWN) {
                if(lastEnter == 0) {
                    lastEnter = System.currentTimeMillis();
                } else {
                    long difference = System.currentTimeMillis() - lastEnter;
                    lastEnter = System.currentTimeMillis();
                    if(difference < 350) {
                        return true;
                    }
                }
            }

            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE || actionId == KeyEvent.ACTION_DOWN) {
                onNewInput();
            }

//                if (event == null && actionId == EditorInfo.IME_NULL) onNewInput();
//                if (event != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER) onNewInput();

            return true;
        });
//        Ctrl+C on a physical keyboard, unless some text is selected (then it copies as usual)
        this.mInputView.setOnKeyListener((v1, keyCode, event) -> {
            if(keyCode == KeyEvent.KEYCODE_TAB && onTab != null) {
                if(event.getAction() == KeyEvent.ACTION_DOWN) onTab.run();
                return true;
            }
            if(keyCode == KeyEvent.KEYCODE_C && event.isCtrlPressed() && event.getAction() == KeyEvent.ACTION_DOWN
                    && mInputView.getSelectionStart() == mInputView.getSelectionEnd()) {
                sendCtrlC();
                return true;
            }
            return false;
        });
//        if(autoLowerFirstChar) {
//            this.mInputView.setFilters(new InputFilter[] {new InputFilter() {
//                @Override
//                public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
//                    if (dstart == 0 && dend == 0 && start == 0 && end == 1 && source.length() > 0) {
//                        return TextUtils.concat(source.toString().toLowerCase().charAt(0) + Tuils.EMPTYSTRING, source.subSequence(1,source.length()));
//                    }
//
//                    return source;
//                }
//            }});
//        }
    }

    private final List<Object> highlights = new ArrayList<>();

//    findout [word]: highlights the word in the output, scrolls to the last one. No word: removes the highlights
    public void highlight(final String word) {
        mTerminalView.post(() -> {
            CharSequence text = mTerminalView.getText();
            if(!(text instanceof Spannable)) return;
            Spannable spannable = (Spannable) text;

            for(Object span : highlights) spannable.removeSpan(span);
            highlights.clear();
            if(word == null || word.length() == 0) return;

//            don't count the "findout ..." line just typed
            String content = text.toString().toLowerCase();
            String needle = word.toLowerCase();
            int limit = content.lastIndexOf("findout");
            if(limit == -1) limit = content.length();
            else limit = content.lastIndexOf('\n', limit) + 1;

            int last = -1, count = 0;
            for(int i = content.indexOf(needle); i != -1 && i < limit; i = content.indexOf(needle, i + needle.length())) {
                Object bg = new android.text.style.BackgroundColorSpan(0xCCFFD54F);
                Object fg = new ForegroundColorSpan(Color.BLACK);
                spannable.setSpan(bg, i, i + needle.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                spannable.setSpan(fg, i, i + needle.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                highlights.add(bg);
                highlights.add(fg);
                last = i;
                count++;
            }

            Tuils.sendOutput(mContext, count == 0 ? "No match for " + word : count + (count == 1 ? " match" : " matches") + " (findout to clear)", CATEGORY_OUTPUT);

            if(last != -1 && mTerminalView.getLayout() != null) {
                final int y = mTerminalView.getLayout().getLineTop(mTerminalView.getLayout().getLineForOffset(last));
                mScrollView.removeCallbacks(mScrollRunnable);
                mScrollView.postDelayed(() -> mScrollView.smoothScrollTo(0, Math.max(0, mTerminalView.getTop() + y - mScrollView.getHeight() / 3)), 400);
            }
        });
    }

    private Runnable onTab;

    public void setOnTab(Runnable onTab) {
        this.onTab = onTab;
    }

    private void sendCtrlC() {
        writeToView("^C", CATEGORY_INPUT);
        executer.execute(ProcessInterrupter.CTRL_C, null);
    }

    private void setupNewInput() {
        mInputView.setText(Tuils.EMPTYSTRING);

        if(defaultHint) {
            mInputView.setHint(Tuils.getHint(mainPack.currentDirectory.getAbsolutePath()));
        }

        requestInputFocus();
    }

    private boolean onNewInput() {
        if (mInputView == null) {
            return false;
        }

        CharSequence input = mInputView.getText();

        String cmd = input.toString().trim();

        Object obj = null;
        try {
            obj = ((Spannable) input).getSpans(0, input.length(), AppsManager.LaunchInfo.class)[0];
        } catch (Exception e) {
//            an error will probably be thrown everytime, but we don't need to track it
        }

//        passwords typed for ssh are never shown, nor kept in the history
        if(secretInput) {
            executer.execute(input.toString(), null);
            setupNewInput();
            return true;
        }

        if(input.length() > 0) {
            clearCmdsCount++;
            if(clearCmdsCount != 0 && clearAfterCmds > 0 && clearCmdsCount % clearAfterCmds == 0) clear();

            writeToView(input, CATEGORY_INPUT);

            if(cmdList.size() == CMD_LIST_SIZE) {
                cmdList.remove(0);
            }
            cmdList.add(cmdList.size(), cmd);
            howBack = -1;

            if(HistoryManager.instance != null) HistoryManager.instance.add(cmd);
        }

//        DO NOT USE THE INTENT APPROACH
//        apps are not launching properly, when one has been launched, an other attempt will show always the same
//
//        Intent intent = new Intent(MainManager.ACTION_EXEC);
//        intent.putExtra(MainManager.CMD, cmd);
//        intent.putExtra(MainManager.CMD_COUNT, MainManager.commandCount);
//
//        Parcelable p = null;
//        if(obj instanceof Parcelable) p = (Parcelable) obj;
//        intent.putExtra(MainManager.PARCELABLE, p);
//
//        LocalBroadcastManager.getInstance(mContext.getApplicationContext()).sendBroadcast(intent);

        executer.execute(cmd, obj);

//        because it will clear suggestions without refilling them, because "aftertextchanged" wont be called
//        if(cmd.length() > 0) LocalBroadcastManager.getInstance(mContext.getApplicationContext()).sendBroadcast(new Intent(UIManager.ACTION_CLEAR_SUGGESTIONS));

        setupNewInput();

        return true;
    }

    public void setOutput(CharSequence output, int type) {
        if (output == null || output.length() == 0) return;

        writeToView(output, type);
    }

    public void setOutput(int color, CharSequence output) {
        if(output == null || output.length() == 0) return;

        if(color == TerminalManager.NO_COLOR) {
            color = XMLPrefsManager.getColor(Theme.output_color);
        }

        SpannableString si = new SpannableString(output);
        si.setSpan(new ForegroundColorSpan(color), 0, output.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        CharSequence s = TextUtils.concat(Tuils.NEWLINE, si);
        writeToView(s);
    }

    public String getTerminalText() {
        return mTerminalView.getText().toString();
    }

    public void onBackPressed() {
        if(cmdList.size() > 0) {

            if(howBack == -1) {
                howBack = cmdList.size();
            } else if(howBack == 0) {
                return;
            }
            howBack--;

            setInput(cmdList.get(howBack));
        }
    }

    public void onNextPressed() {
        if(howBack != -1 && howBack < cmdList.size()) {
            howBack++;

            String input;
            if(howBack == cmdList.size()) {
                input = Tuils.EMPTYSTRING;
            } else {
                input = cmdList.get(howBack);
            }

            setInput(input);
        }
    }

    public static final String FORMAT_INPUT = "%i";
    public static final String FORMAT_OUTPUT = "%o";
    public static final String FORMAT_PREFIX = "%p";
    public static final String FORMAT_NEWLINE = "%n";

    private void writeToView(CharSequence text, int type) {
        text = getFinalText(text, type);
        text = TextUtils.concat(Tuils.NEWLINE, text);
        writeToView(text, type == CATEGORY_INPUT);
    }

    private void writeToView(final CharSequence text) {
        writeToView(text, false);
    }

    private void writeToView(final CharSequence text, boolean instant) {
        if(!typingAnimation) {
            mTerminalView.post(() -> {
                mTerminalView.append(text);
                scrollToEnd();
            });
            return;
        }

//        typing animation: everything goes through one queue, so the order is kept
        synchronized (typingQueue) {
            typingQueue.add(new Object[] {text, instant});
            typingPending += text.length();
        }
        mTerminalView.post(typingRunnable);
    }

//    a span object can be attached only once: appending pieces of the same text would move the color
//    from one piece to the next, so every piece gets its own copy of the styles
    private static CharSequence typingChunk(CharSequence text, int start, int end) {
        CharSequence piece = text.subSequence(start, end);
        if(start == 0 && end == text.length() || !(piece instanceof Spanned)) return piece;

        Spanned spanned = (Spanned) piece;
        SpannableString copy = new SpannableString(piece.toString());
        for(Object span : spanned.getSpans(0, piece.length(), Object.class)) {
            Object style = span instanceof android.text.style.CharacterStyle && !(span instanceof android.text.style.ClickableSpan)
                    ? android.text.style.CharacterStyle.wrap((android.text.style.CharacterStyle) span) : span;
            copy.setSpan(style, spanned.getSpanStart(span), spanned.getSpanEnd(span), spanned.getSpanFlags(span));
        }
        return copy;
    }

    private static final int TYPING_FRAME_MS = 16;
//    when this much text is waiting (ping, logs...), it's shown at once instead of typed
    private static final int TYPING_MAX_PENDING = 1500;

    private boolean typingAnimation;
    private int typingCharsPerFrame;
    private final java.util.LinkedList<Object[]> typingQueue = new java.util.LinkedList<>();
    private int typingPending, typingPosition;

    private final Runnable typingRunnable = new Runnable() {
        @Override
        public void run() {
            mTerminalView.removeCallbacks(this);

            Object[] head;
            boolean flush;
            synchronized (typingQueue) {
                head = typingQueue.peek();
                flush = typingPending > TYPING_MAX_PENDING;
            }
            if(head == null) return;

            CharSequence text = (CharSequence) head[0];
            int end = flush || (Boolean) head[1] ? text.length() : Math.min(text.length(), typingPosition + typingCharsPerFrame);

            mTerminalView.append(typingChunk(text, typingPosition, end));
            synchronized (typingQueue) {
                typingPending -= end - typingPosition;
                if(end >= text.length()) {
                    typingQueue.poll();
                    typingPosition = 0;
                } else {
                    typingPosition = end;
                }
            }
            scrollToEnd();

            mTerminalView.postDelayed(this, flush ? 0 : TYPING_FRAME_MS);
        }
    };

    private CharSequence getFinalText(CharSequence t, int type) {
        CharSequence s;
        switch (type) {
            case CATEGORY_INPUT:
                t = t.toString();

                boolean su = t.toString().startsWith("su ") || suMode;

                SpannableString si = Tuils.span(inputFormat, inputColor);
                if(clickCommands || longClickCommands) si.setSpan(new LongClickableSpan(clickCommands ? t.toString() : null, longClickCommands ? t.toString() : null, PrivateIOReceiver.ACTION_INPUT), 0,
                        si.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                s = TimeManager.instance.replace(si);
                s = TextUtils.replace(s,
                        new String[] {FORMAT_INPUT, FORMAT_PREFIX, FORMAT_NEWLINE, FORMAT_INPUT.toUpperCase(), FORMAT_PREFIX.toUpperCase(), FORMAT_NEWLINE.toUpperCase()},
                        new CharSequence[] {t, su ? suPrefix : prefix, Tuils.NEWLINE, t, su ? suPrefix : prefix, Tuils.NEWLINE});

                break;
            case CATEGORY_OUTPUT:
                t = t.toString();

                SpannableString so = Tuils.span(outputFormat, outputColor);

                s = TextUtils.replace(so,
                        new String[] {FORMAT_OUTPUT, FORMAT_NEWLINE, FORMAT_OUTPUT.toUpperCase(), FORMAT_NEWLINE.toUpperCase()},
                        new CharSequence[] {t, Tuils.NEWLINE, t, Tuils.NEWLINE});

                break;
//            already colored here
            case CATEGORY_NO_COLOR:
                s = t;
                break;
            default:
                return null;
        }

        return s;
    }

    public void simulateEnter() {
        onNewInput();
    }

    public String getInput() {
        return mInputView.getText().toString();
    }

    public void setInput(String input, Object obj) {
        SpannableString spannable = new SpannableString(input);
        if(obj != null) {
            spannable.setSpan(obj, 0, input.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        mInputView.setText(spannable);
        focusInputEnd();
    }

    public void setInput(String input) {
        setInput(input, null);
    }

    public void setHint(String hint) {
        defaultHint = false;

        if(mInputView != null) {
            mInputView.setHint(hint);
        }
    }

    public void setDefaultHint() {
        defaultHint = true;

        if(mInputView != null) {
            mInputView.setHint(Tuils.getHint(mainPack.currentDirectory.getAbsolutePath()));
        }
    }

    public void focusInputEnd() {
        mInputView.setSelection(getInput().length());
    }

    public void scrollToEnd() {
        mScrollView.postDelayed(mScrollRunnable, SCROLL_DELAY);
    }

    public void requestInputFocus() {
        mInputView.requestFocus();
    }

    public IBinder getInputWindowToken() {
        return mInputView.getWindowToken();
    }

    public View getInputView() {
        return mInputView;
    }

    public void clear() {
        synchronized (typingQueue) {
            typingQueue.clear();
            typingPending = 0;
            typingPosition = 0;
        }
        mTerminalView.post(() -> mTerminalView.setText(Tuils.EMPTYSTRING));
        cmdList.clear();
        clearCmdsCount = 0;
    }

    public void onRoot() {
        ((Activity) mContext).runOnUiThread(() -> {
            suMode = true;
            mPrefix.setText(suPrefix.endsWith(Tuils.SPACE) ? suPrefix : suPrefix + Tuils.SPACE);
        });
    }

    private boolean secretInput = false;
    private int normalInputType = -1;

    public void setSecretInput(boolean secret) {
        ((Activity) mContext).runOnUiThread(() -> {
            if(normalInputType == -1) normalInputType = mInputView.getInputType();

            secretInput = secret;
            mInputView.setInputType(secret
                    ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                    : normalInputType);
            focusInputEnd();
        });
    }

//    null restores the normal prefix
    public void setRemotePrefix(String remotePrefix) {
        ((Activity) mContext).runOnUiThread(() -> {
            String p = remotePrefix != null ? remotePrefix : (suMode ? suPrefix : prefix);
            mPrefix.setText(p.endsWith(Tuils.SPACE) ? p : p + Tuils.SPACE);
        });
    }

    public void onStandard() {
        ((Activity) mContext).runOnUiThread(() -> {
            suMode = false;
            mPrefix.setText(prefix.endsWith(Tuils.SPACE) ? prefix : prefix + Tuils.SPACE);
        });
    }
}
