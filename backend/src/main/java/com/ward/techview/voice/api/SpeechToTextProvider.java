package com.ward.techview.voice.api;

public interface SpeechToTextProvider {
    TranscriptionResult transcribe(AudioObject audioObject);
}
