package com.example.traductor;

import android.content.Context;
import android.media.MediaPlayer;
import android.util.Base64;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;

public class AudioHandler {
    private Context context;
    private ArrayList<byte[]> audioChunks = new ArrayList<>();
    private int totalChunks = 0;
    private MediaPlayer mediaPlayer;
    public AudioHandler(Context context) {
        this.context = context;
    }

    public void handleAudioMessage(String message) {
        try {
            JSONObject jsonMessage = new JSONObject(message);

            if (jsonMessage.has("total_chunks")) {
                totalChunks = jsonMessage.getInt("total_chunks");
            } else if (jsonMessage.has("fragment_index") && jsonMessage.has("data")) {
                int fragmentIndex = jsonMessage.getInt("fragment_index");
                String encodedData = jsonMessage.getString("data");

                byte[] decodedChunk = Base64.decode(encodedData, Base64.DEFAULT);

                while (audioChunks.size() <= fragmentIndex) {
                    audioChunks.add(null);
                }
                audioChunks.set(fragmentIndex, decodedChunk);

                if (allChunksReceived()) {
                    reconstructAudioFile();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean allChunksReceived() {
        if (audioChunks.size() != totalChunks) return false;

        for (byte[] chunk : audioChunks) {
            if (chunk == null) return false;
        }
        return true;
    }

    private void reconstructAudioFile() {
        try {
            File outputFile = new File("/storage/emulated/0/Download/output_audio.mp3");
            FileOutputStream fos = new FileOutputStream(outputFile);
            for (byte[] chunk : audioChunks) {
                fos.write(chunk);
            }
            fos.close();

            playAudio(outputFile.getPath());
            // Limpiar los fragmentos y reiniciar el contador
            audioChunks.clear();
            totalChunks = 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void playAudio(String filePath) {
        try {
            // Liberar el MediaPlayer si ya existe
            if (mediaPlayer != null) {
                mediaPlayer.reset();
                mediaPlayer.release();
            }

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(filePath);
            mediaPlayer.prepare();
            mediaPlayer.start();

            mediaPlayer.setOnCompletionListener(mp -> {
                mp.release();
                mediaPlayer = null;
            });

        } catch (Exception e) {
            e.printStackTrace();
            if (mediaPlayer != null) {
                mediaPlayer.release();
                mediaPlayer = null;
            }
            Toast.makeText(context, "Error al reproducir audio: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

}
