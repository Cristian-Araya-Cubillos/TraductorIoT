package com.example.traductor;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
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

import android.widget.ArrayAdapter;
import android.widget.Spinner;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private ImageView iv_mic;
    private TextView tv_Speech_to_text;
    private TextView tvMqttMessage;
    private Spinner spinnerInputLanguage, spinnerOutputLanguage;
    private static final int REQUEST_CODE_SPEECH_INPUT = 1;

    private static final String BROKER_URL="tcp://192.168.1.37:1883";
    private static final String CLIENT="Android";
    private static final String USUARIO="usuario";
    private static final String CONTRA="1234321";
    private MqttHandler mqttHandler;

    private AudioHandler audioHandler;

    // Mapa para asociar el nombre del idioma con su código correspondiente
    private final Map<String, String> languageCodeMap = new HashMap<String, String>() {{
        put("Español", "es-CL");
        put("Inglés", "en-US");
        put("Francés", "fr-FR");
        put("Alemán", "de-DE");
        put("Japonés", "ja-JP");
    }};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        iv_mic = findViewById(R.id.btnMic);
        tv_Speech_to_text = findViewById(R.id.etTextFromMic);
        tvMqttMessage = findViewById(R.id.tvMqttMessage);
        spinnerInputLanguage = findViewById(R.id.spinnerInputLang);
        spinnerOutputLanguage = findViewById(R.id.spinnerOutputLang);

        mqttHandler = new MqttHandler();
        audioHandler = new AudioHandler(this);

        mqttHandler.connect(BROKER_URL, CLIENT, USUARIO, CONTRA);
        mqttHandler.subscribe("server/response");
        mqttHandler.subscribe("server/msg");

        // Configurar opciones de idiomas
        String[] languages = {"Español", "Inglés", "Francés", "Alemán", "Japonés"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, languages);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerInputLanguage.setAdapter(adapter);
        spinnerOutputLanguage.setAdapter(adapter);

        iv_mic.setOnClickListener(v -> {
            // Obtener el código de idioma correspondiente a la selección del usuario
            String inputLanguage = languageCodeMap.get(spinnerInputLanguage.getSelectedItem().toString());
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, inputLanguage);
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Escuchando...");

            try {
                startActivityForResult(intent, REQUEST_CODE_SPEECH_INPUT);
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        mqttHandler.setMessageListener((topic, message) -> {
            if (topic.equals("server/response")) {
                audioHandler.handleAudioMessage(message);
                runOnUiThread(this::enableUserInteraction); // Habilitar interacción
            } else if (topic.equals("server/msg")) {
                runOnUiThread(() -> tvMqttMessage.setText(message));
            } else {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Topic desconocido: " + topic, Toast.LENGTH_SHORT).show());
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SPEECH_INPUT && resultCode == RESULT_OK && data != null) {
            ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            String message = Objects.requireNonNull(result).get(0);
            tv_Speech_to_text.setText(message);
            disableUserInteraction();

            // Obtener el código de idioma correspondiente a la salida seleccionada
            String outputLanguage = languageCodeMap.get(spinnerOutputLanguage.getSelectedItem().toString());
            mqttHandler.publish("client/messages", message + "|" + outputLanguage);
        }
    }

    private void disableUserInteraction() {
        findViewById(R.id.btnMic).setEnabled(false);
        findViewById(R.id.etTextFromMic).setEnabled(false);

        ProgressBar progressBar = findViewById(R.id.progressBar);
        progressBar.setVisibility(View.VISIBLE);
    }

    public void enableUserInteraction() {
        findViewById(R.id.btnMic).setEnabled(true);
        findViewById(R.id.etTextFromMic).setEnabled(true);
        ProgressBar progressBar = findViewById(R.id.progressBar);
        progressBar.setVisibility(View.GONE);
    }
}
