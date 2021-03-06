package com.github.uiautomator;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.IBinder;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import com.github.uiautomator.monitor.HttpPostNotifier;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Random;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class FastInputIME extends InputMethodService {
    private static final String TAG = "FastInputIME";
    private BroadcastReceiver mReceiver = null;
    protected OkHttpClient httpClient = new OkHttpClient();

    @Override
    public View onCreateInputView() {
        KeyboardView keyboardView = (KeyboardView) getLayoutInflater().inflate(R.layout.keyboard, null);
        IntentFilter filter = new IntentFilter();
        filter.addAction("ADB_INPUT_TEXT");
        filter.addAction("ADB_INPUT_KEYCODE");
        filter.addAction("ADB_CLEAR_TEXT");
        filter.addAction("ADB_SET_TEXT"); // Equals to: Clear then Input
        // TODO: filter.addAction("ADB_INPUT_CHARS");
        // TODO: filter.addAction("ADB_EDITOR_CODE");
        // NONEED: filter.addAction(USB_STATE_CHANGE);
        mReceiver = new InputMessageReceiver();
        registerReceiver(mReceiver, filter);

        Keyboard keyboard = new Keyboard(this, R.xml.keyboard);
        keyboardView.setKeyboard(keyboard);
        keyboardView.setOnKeyboardActionListener(new MyKeyboardActionListener());

        return keyboardView;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        String text = getText();
        makeToast("StartInputView: text -- " + text);
        sendRequestToATXAgent("I"+text);
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        // send message
        makeToast("FinishInputView");
        sendRequestToATXAgent("F");
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false;
    }

    class InputMessageReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String msgText;
            InputConnection ic = getCurrentInputConnection();
            if (ic == null) {
                return;
            }
            switch (action) {
                case "ADB_INPUT_TEXT":
                    /* test method
                     * TEXT=$(echo -n "Hello World" | base64)
                     * adb shell am broadcast -a ADB_INPUT_TEXT --es text ${TEXT:-"SGVsbG8gd29ybGQ="}
                     */
                    msgText = intent.getStringExtra("text");
                    if (msgText == null) {
                        return;
                    }
                    Log.i(TAG, "input text(base64): " + msgText);
                    inputTextBase64(msgText);
                    break;
                case "ADB_INPUT_KEYCODE":
                    /* test method
                     * Enter code 66
                     * adb shell am broadcast -a ADB_INPUT_KEYCODE --ei code 66
                     */
                    int code = intent.getIntExtra("code", -1);
                    if (code != -1) {
                        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
                    }
                case "ADB_CLEAR_TEXT":
                    Log.i(TAG, "receive ADB_CLEAR_TEXT");
                    clearText();
                    break;
                case "ADB_SET_TEXT":
                    Log.i(TAG, "receive ADB_SET_TEXT");
                    msgText = intent.getStringExtra("text");
                    if (msgText == null) {
                        msgText = "";
                    }
                    Log.i(TAG, "input text(base64): " + msgText);
                    ic.beginBatchEdit();
                    clearText();
                    inputTextBase64(msgText);
                    ic.endBatchEdit();
            }
        }
    }

    // Refs: https://www.jianshu.com/p/892168a57fe3
    private class MyKeyboardActionListener implements KeyboardView.OnKeyboardActionListener {

        @Override
        public void onPress(int i) {
        }

        @Override
        public void onRelease(int i) {
        }

        @Override
        public void onKey(int primaryCode, int[] keyCodes) {
            if (primaryCode == Keyboard.KEYCODE_CANCEL) {
                Log.d(TAG, "Keyboard CANCEL not implemented");
            } else if (primaryCode == -10) {
                clearText();
            } else if (primaryCode == -5) {
                changeInputMethod();
                // switchToLastInputMethod();
            } else if (primaryCode == -7) {
                InputConnection ic = getCurrentInputConnection();
                ic.commitText(randomString(1), 0);
            } else {
                Log.w(TAG, "Unknown primaryCode " + primaryCode);
            }
        }

        @Override

        public void onText(CharSequence charSequence) {
        }

        @Override
        public void swipeLeft() {
        }

        @Override
        public void swipeRight() {
        }

        @Override
        public void swipeDown() {
        }

        @Override
        public void swipeUp() {
        }
    }

    private void inputTextBase64(String base64text) {
        byte[] data = Base64.decode(base64text, Base64.DEFAULT);
        try {
            String text = new String(data, "UTF-8");
            InputConnection ic = getCurrentInputConnection();
            ic.commitText(text, 1);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    private void clearText() {
        // Refs: https://stackoverflow.com/questions/33082004/android-custom-soft-keyboard-how-to-clear-edit-text-commited-text
        InputConnection ic = getCurrentInputConnection();
        CharSequence currentText = ic.getExtractedText(new ExtractedTextRequest(), 0).text;
        Log.d(TAG, "Current text: " + currentText);
        CharSequence beforCursorText = ic.getTextBeforeCursor(currentText.length(), 0);
        CharSequence afterCursorText = ic.getTextAfterCursor(currentText.length(), 0);
        ic.deleteSurroundingText(beforCursorText.length(), afterCursorText.length());
    }

    private String getText() {
        String text = "";
        try {
            InputConnection ic = getCurrentInputConnection();
            ExtractedTextRequest req = new ExtractedTextRequest();
            req.hintMaxChars = 100000;
            req.hintMaxLines = 10000;
            req.flags = 0;
            req.token = 0;
            text = ic.getExtractedText(req, 0).text.toString();
        } catch (Throwable t) {
        }
        return text;
    }

    private void changeInputMethod() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showInputMethodPicker();
    }

    private void switchToLastInputMethod() {
        final IBinder token = getWindow().getWindow().getAttributes().token;
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.switchToLastInputMethod(token);
    }

    private void makeToast(String msg) {
        // Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void sendRequestToATXAgent(String text) {
        RequestBody body = RequestBody.create(MediaType.parse("text/plain; charset=utf-8"), text);
        Request request = new Request.Builder()
                .url("http://127.0.0.1:7912/whatsinput")
                .post(body)
                .build();
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Log.i(TAG, "StartInputView text send to atx-agent");
            }
        });
    }

    public String randomString(int length) {
        String str = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < length; i++) {
            int num = random.nextInt(62);
            buf.append(str.charAt(num));
        }
        return buf.toString();
    }
}
