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
import java.util.UUID;

import androidx.appcompat.app.AlertDialog;

public class MainActivity extends AppCompatActivity {
    private String deviceId;
    private ImageView iv_mic;
    private TextView tv_Speech_to_text;
    private TextView tvMqttMessage;
    private Spinner spinnerInputLanguage, spinnerOutputLanguage;
    private static final int REQUEST_CODE_SPEECH_INPUT = 1;

    private static final String BROKER_URL="ssl://ddaffad6939b409da8e8aa4114aa359f.s1.eu.hivemq.cloud:8883";
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
        deviceId = UUID.randomUUID().toString(); // Genera un UUID único
        iv_mic = findViewById(R.id.btnMic);
        tv_Speech_to_text = findViewById(R.id.etTextFromMic);
        tvMqttMessage = findViewById(R.id.tvMqttMessage);
        spinnerInputLanguage = findViewById(R.id.spinnerInputLang);
        spinnerOutputLanguage = findViewById(R.id.spinnerOutputLang);

        ImageView btnInfo = findViewById(R.id.btnInfo);
        btnInfo.setOnClickListener(v -> showInstructionsDialog());

        mqttHandler = new MqttHandler();
        audioHandler = new AudioHandler(this);

        mqttHandler.connect(BROKER_URL, CLIENT, USUARIO, CONTRA);
        mqttHandler.subscribe("server/response"+deviceId);
        mqttHandler.subscribe("server/msg"+deviceId);

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
            if (topic.equals("server/response"+deviceId)) {
                //System.out.println("Entro a AUDIO" + message);
                audioHandler.handleAudioMessage(message);
                runOnUiThread(this::enableUserInteraction); // Habilitar interacción
            } else if (topic.equals("server/msg"+deviceId)) {
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

            // Obtener el código de idioma correspondiente a la entrada seleccionada
            String inputLanguage = languageCodeMap.get(spinnerInputLanguage.getSelectedItem().toString());
            String shortInputLanguage = inputLanguage.substring(0, 2); // Solo las dos primeras letras

            // Obtener el código de idioma correspondiente a la salida seleccionada
            String outputLanguage = languageCodeMap.get(spinnerOutputLanguage.getSelectedItem().toString());
            String shortOutputLanguage = outputLanguage.substring(0, 2); // Solo las dos primeras letras

            // Publicar mensaje al servidor incluyendo idiomas de entrada y salida
            mqttHandler.publish("client/messages", message + "|" + shortInputLanguage + "|" + shortOutputLanguage +"|" +deviceId);
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

    private void showInstructionsDialog() {
        String instructions = "Instrucciones para usar la aplicación:\n\n" +
                "1. Selecciona el idioma de entrada y el idioma de salida utilizando los menús desplegables.\n" +
                "2. Presiona el botón del micrófono para comenzar a hablar en el idioma seleccionado como entrada.\n" +
                "3. La aplicación transcribirá tu voz y la enviará al servidor para traducirla al idioma de salida.\n" +
                "4. Si el servidor envía un audio como respuesta, este será reproducido automáticamente.\n\n" +
                "Nota: Asegúrate de tener una conexión a internet activa.";

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Cómo usar la aplicación")
                .setMessage(instructions)
                .setPositiveButton("Entendido", (dialog, which) -> dialog.dismiss())
                .setCancelable(true)
                .show();
    }
}
