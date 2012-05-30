package net.bible.android.device;

import java.util.HashMap;
import java.util.Locale;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.control.event.apptobackground.AppToBackgroundEvent;
import net.bible.android.control.event.apptobackground.AppToBackgroundListener;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import net.bible.android.view.activity.base.Dialogs;
import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.Log;

/**
 * <p>text-to-speech (TTS). Please note the following steps:</p>
 *
 * <ol>
 * <li>Construct the TextToSpeech object.</li>
 * <li>Handle initialization callback in the onInit method.
 * The activity implements TextToSpeech.OnInitListener for this purpose.</li>
 * <li>Call TextToSpeech.speak to synthesize speech.</li>
 * <li>Shutdown TextToSpeech in onDestroy.</li>
 * </ol>
 *
 * <p>Documentation:
 * http://developer.android.com/reference/android/speech/tts/package-summary.html
 * </p>
 * <ul>
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.

 */
public class TextToSpeechController implements TextToSpeech.OnInitListener, TextToSpeech.OnUtteranceCompletedListener, AppToBackgroundListener {

    private static final String TAG = "TextToSpeechController";

    private TextToSpeech mTts;

    private Locale speechLocale;
    private SpeakTextProvider mSpeakTextProvider;

    private Context context;

    private long uniqueUtteranceNo = 0;
    private String mLatestUtteranceId = "";
    // tts.isSpeaking() returns false when multiple text is queued on some older versions of Android so maintain it manually
    private boolean isSpeaking = false;
    
    private static final TextToSpeechController singleton = new TextToSpeechController();
    
    public static TextToSpeechController getInstance() {
    	return singleton;
    }
    
    private TextToSpeechController() {
    	context = BibleApplication.getApplication().getApplicationContext();
    	CurrentActivityHolder.getInstance().addAppToBackgroundListener(this);
    	mSpeakTextProvider = new SpeakTextProvider();
    }

    public void speak(Locale speechLocale, String textToSpeak, boolean queue) {
   		if (!queue) {
   			Log.d(TAG, "Queue is false so requesting stop");
   			stop();
   		}
   		mSpeakTextProvider.addTextToSpeak(textToSpeak);

    	if (mTts==null) {
    		// currently can't change Locale until speech ends
        	this.speechLocale = speechLocale;
	    	try {
		        // Initialize text-to-speech. This is an asynchronous operation.
		        // The OnInitListener (second argument) is called after initialization completes.
		        mTts = new TextToSpeech(context,
		            this  // TextToSpeech.OnInitListener
		            );
	    	} catch (Exception e) {
	    		showError(R.string.error_occurred);
	    	}
    	} else {
   			speakAllText();
    	}
    }

    // Implements TextToSpeech.OnInitListener.
    @Override
    public void onInit(int status) {
        // status can be either TextToSpeech.SUCCESS or TextToSpeech.ERROR.
        if (status == TextToSpeech.SUCCESS) {
	    	Log.d(TAG, "Speech locale:"+speechLocale);
            int result = mTts.setLanguage(speechLocale);
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED) {
    	    	Log.e(TAG, "TTS missing or not supported ("+result+")");
               // Language data is missing or the language is not supported.
                showError(R.string.tts_lang_not_available);
            } else {
                // The TTS engine has been successfully initialized.
            	int ok = mTts.setOnUtteranceCompletedListener(this);
            	if (ok==TextToSpeech.ERROR) {
            		Log.e(TAG, "Error registering onUtteranceCompletedListener");
            	}
            	
            	// say the text
           		speakAllText();
            }
        } else {
            // Initialization failed.
            showError(R.string.error_occurred);
        }
    }

    private void speakAllText() {
        // ask TTs to say the text
    	while (mSpeakTextProvider.isMoreTextToSpeak()) {
    		String text = mSpeakTextProvider.getNextTextToSpeak();
    		speakString(text);
    	}
    	mSpeakTextProvider.reset();
    }

    private void speakString(String text) {
    	// Always set the UtteranceId (or else OnUtteranceCompleted will not be called)
        HashMap<String, String> dummyTTSParams = new HashMap<String, String>();
        String utteranceId = "AND-BIBLE-"+uniqueUtteranceNo++;
        dummyTTSParams.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);

        mTts.speak(text,
                TextToSpeech.QUEUE_ADD, // handle flush by clearing text queue 
                dummyTTSParams);
        
        mLatestUtteranceId = utteranceId;
        isSpeaking = true;
        Log.d(TAG, "Speaking:"+text);
    }
    private void showError(int msgId) {
    	Dialogs.getInstance().showErrorMsg(msgId);
    }

	public void stop() {
    	Log.d(TAG, "Stop TTS");
		
        // Don't forget to shutdown!
        if (isSpeaking()) {
        	Log.d(TAG, "Flushing speech");
        	// flush remaining text
	        mTts.speak(" ", TextToSpeech.QUEUE_FLUSH, null);
        }
        
        mSpeakTextProvider.reset();
        isSpeaking = false;
	}

	@Override
	public void onUtteranceCompleted(String utteranceId) {
		Log.d(TAG, "onUtteranceCompleted:"+utteranceId);
		if (mLatestUtteranceId.equals(utteranceId)) {
			Log.d(TAG, "Shutting down TTS");
			shutdown();
		}
	}

    public void shutdown() {
    	Log.d(TAG, "Shutdown TTS");
		
        // Don't forget to shutdown!
        if (mTts != null) {
            mTts.stop();
            mTts.shutdown();
            mTts = null;
        }
        
        mSpeakTextProvider.reset();
        isSpeaking = false;
    }

	public boolean isSpeaking() {
		return isSpeaking;
	}

	@Override
	public void applicationNowInBackground(AppToBackgroundEvent e) {
		shutdown();		
	}
}
