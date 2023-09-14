package jsc.org.lib.camera.utils;

import android.content.Context;
import android.media.AudioManager;
import android.media.SoundPool;

import androidx.annotation.StringDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashMap;
import java.util.Map;

import jsc.org.lib.camera.R;

/**
 * 声音反馈管理器
 *
 * @author jsc
 */
public class SoundPoolPlayer {

    private static SoundPoolPlayer instance = null;
    private SoundPool mSoundPool = null;
    private  int m_nSuccessID;
    private int m_nErrorID;
    private int m_nShutter;

    private SoundPoolPlayer() {

    }

    public static SoundPoolPlayer getInstance() {
        if (instance == null) {
            instance = new SoundPoolPlayer();
        }
        return instance;
    }

    public void register(Context context){
        loadPromptSound(context);
    }

    public void unregister(){
        if (mSoundPool != null) {
            mSoundPool.release();
            mSoundPool = null;
        }
    }

    private void loadPromptSound(Context context) {
        if (mSoundPool == null) {
            //使用application context防止内存泄漏
            mSoundPool = new SoundPool.Builder()
                    .setMaxStreams(10)
                    .build();
            m_nSuccessID = mSoundPool.load(context.getApplicationContext(), R.raw.success, 1);
            m_nErrorID = mSoundPool.load(context.getApplicationContext(), R.raw.error, 1);
            m_nShutter = mSoundPool.load(context.getApplicationContext(), R.raw.shutter, 1);
        }
    }

    public void playSucVoice() {
        mSoundPool.play(m_nSuccessID, 1, 1, 0, 0, 1);
    }

    public void playErrorVoice() {
        mSoundPool.play(m_nErrorID, 1, 1, 0, 0, 1);
    }

    public void playShutterVoice() {
        mSoundPool.play(m_nShutter, 1, 1, 0, 0, 1);
    }

    public void playVoice(int id) {
        mSoundPool.play(id, 1, 1, 0, 0, 1);
    }
}
