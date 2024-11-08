package com.example.traductor;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;

import java.io.FileOutputStream;
import org.json.JSONObject;
import android.util.Base64;

public class MainActivity extends AppCompatActivity {
    private ImageView iv_mic;
    private TextView tv_Speech_to_text;
    private TextView tvMqttMessage;
    private static final int REQUEST_CODE_SPEECH_INPUT = 1;

    private static final String BROKER_URL="tcp://192.168.1.37:1883";
    private static final String CLIENT="Android";
    private static final String USUARIO="usuario";
    private static final String CONTRA="1234321";
    private MqttHandler mqttHandler;

    private ArrayList<byte[]> audioChunks = new ArrayList<>();
    private int totalChunks = 0;  // Número total de fragmentos

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mqttHandler = new MqttHandler();
        mqttHandler.connect(BROKER_URL, CLIENT, USUARIO, CONTRA);

        // Set up the callback for incoming messages
        mqttHandler.setMessageListener(new MqttHandler.MessageListener() {
            @Override
            public void onMessage(String topic, String message) {
                runOnUiThread(() -> {
                    try {
                        // Verificar el topic correspondiente
                        if (topic.equals("server/response")) {
                            JSONObject jsonMessage = new JSONObject(message);

                            // Comprobar si el mensaje contiene el número total de fragmentos
                            if (jsonMessage.has("total_chunks")) {
                                totalChunks = jsonMessage.getInt("total_chunks");
                                Toast.makeText(MainActivity.this,
                                        "Número total de fragmentos: " + totalChunks,
                                        Toast.LENGTH_SHORT).show();
                            } else if (jsonMessage.has("fragment_index") && jsonMessage.has("data")) {
                                // Si es un fragmento de audio, procesarlo
                                int fragmentIndex = jsonMessage.getInt("fragment_index");
                                String encodedData = jsonMessage.getString("data");

                                // Decodificar el fragmento en Base64
                                byte[] decodedChunk = Base64.decode(encodedData, Base64.DEFAULT);

                                // Asegurarse de que la lista tiene capacidad para el índice del fragmento
                                while (audioChunks.size() <= fragmentIndex) {
                                    audioChunks.add(null);
                                }

                                // Guardar el fragmento en el índice correspondiente
                                audioChunks.set(fragmentIndex, decodedChunk);

                                // Verificar si todos los fragmentos han sido recibidos
                                if (allChunksReceived()) {
                                    Toast.makeText(MainActivity.this,
                                            "Todos los fragmentos recibidos, reconstruyendo el archivo...",
                                            Toast.LENGTH_SHORT).show();
                                    reconstructAudioFile();
                                }
                            } else {
                                Toast.makeText(MainActivity.this,
                                        "Mensaje desconocido recibido",
                                        Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(MainActivity.this,
                                    "Topic no reconocido: " + topic,
                                    Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        //Toast.makeText(MainActivity.this,"Error al procesar el mensaje: " + e.getMessage(),Toast.LENGTH_LONG).show();
                    }
                });
            }
            // Método para verificar si todos los fragmentos han sido recibidos
            private boolean allChunksReceived() {
                if (audioChunks.size() != totalChunks) return false;

                // Verificar que no haya elementos nulos en la lista
                for (byte[] chunk : audioChunks) {
                    if (chunk == null) return false;
                }
                return true;
            }

        });
        subMessages("server/response");
        iv_mic = findViewById(R.id.btnMic);
        tv_Speech_to_text = findViewById(R.id.etTextFromMic);
        tvMqttMessage = findViewById(R.id.tvMqttMessage);
        iv_mic.setOnClickListener(v -> {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Escuchando...");

            try {
                startActivityForResult(intent, REQUEST_CODE_SPEECH_INPUT);
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, " " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void reconstructAudioFile() {
        //Toast.makeText(MainActivity.this, "ENTRA A RECIBE", Toast.LENGTH_SHORT).show();
        try {
            FileOutputStream fos = new FileOutputStream("/storage/emulated/0/Download/output_audio.mp3");
            //Toast.makeText(MainActivity.this, "ENTRA A TRY", Toast.LENGTH_SHORT).show();
            // Escribir los fragmentos en el archivo en orden
            for (byte[] chunk : audioChunks) {
                fos.write(chunk);
            }

            fos.close();
            System.out.println("Archivo MP3 reconstruido.");

            // Reproducir el archivo MP3
            File outputFile = new File("/storage/emulated/0/Download/output_audio.mp3");
            if (outputFile.exists()) {
                //Toast.makeText(this, "Archivo MP3 reconstruido con éxito: " + "./output_audio.mp3", Toast.LENGTH_LONG).show();
                playAudio("/storage/emulated/0/Download/output_audio.mp3"); // Reproducir el archivo MP3
            } else {
                //Toast.makeText(this, "Error: No se encontró el archivo MP3.", Toast.LENGTH_LONG).show();
            }
            playAudio("/storage/emulated/0/Download/output_audio.mp3");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    // Método para reproducir el archivo MP3
    private void playAudio(String filePath) {
        try {
            MediaPlayer mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(filePath);
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SPEECH_INPUT) {
            if (resultCode == RESULT_OK && data != null) {
                ArrayList<String> result = data.getStringArrayListExtra(
                        RecognizerIntent.EXTRA_RESULTS);
                tv_Speech_to_text.setText(
                        Objects.requireNonNull(result).get(0));
                publishMessage("client/messages", tv_Speech_to_text.getText().toString());

            }
        }
    }

    private void publishMessage(String topic,String message){
        topic = "client/messages";
        Toast.makeText(this,"Publish " + message,Toast.LENGTH_SHORT).show();
        mqttHandler.publish(topic,message);
    }

    private void subMessages(String topic){
        topic = "server/response";

        mqttHandler.subscribe(topic);
        Toast.makeText(this,"Publish " + topic,Toast.LENGTH_SHORT).show();
    }
}