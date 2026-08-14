package com.ward.techview.voice.api.stt;

import com.ward.techview.voice.api.storage.AudioObject;

public interface SpeechToTextProvider {
    TranscriptionResult transcribe(AudioObject audioObject);
}
