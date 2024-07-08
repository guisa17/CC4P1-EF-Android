package com.example.concurrente_ef;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class MainActivity extends AppCompatActivity {
    private static final int PICK_TXT_FILE = 1;
    private static final int REQUEST_PERMISSION = 2;
    private static final String TAG = "MainActivity";
    private TextView textViewStatus;
    private TextView textViewFileName;
    private TextView textViewConnectionStatus;
    private TextView textViewSearchType;
    private EditText editTextKeyword, editTextParam;
    private TCPClient tcpClient;
    private String searchType;
    private String fileName;
    private Uri fileUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button buttonConnect = findViewById(R.id.buttonConnect);
        Button buttonUpload = findViewById(R.id.buttonUpload);
        Button buttonSearch = findViewById(R.id.buttonSearch);
        Button buttonSearchType1 = findViewById(R.id.buttonSearchType1);
        Button buttonSearchType2 = findViewById(R.id.buttonSearchType2);
        Button buttonSearchType3 = findViewById(R.id.buttonSearchType3);

        textViewStatus = findViewById(R.id.textViewStatus);
        textViewFileName = findViewById(R.id.textViewFileName);
        textViewConnectionStatus = findViewById(R.id.textViewConnectionStatus);
        textViewSearchType = findViewById(R.id.textViewSearchType);
        editTextKeyword = findViewById(R.id.editTextKeyword);
        editTextParam = findViewById(R.id.editTextParam);

        buttonConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connectToServer();
            }
        });

        buttonUpload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkPermissionAndOpenFilePicker();
            }
        });

        buttonSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchKeyword();
            }
        });

        buttonSearchType1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchType = "1";
                editTextParam.setVisibility(View.GONE);
                textViewSearchType.setText("Opción seleccionada: Buscar Ocurrencias");
            }
        });

        buttonSearchType2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchType = "2";
                editTextParam.setVisibility(View.GONE);
                textViewSearchType.setText("Opción seleccionada: Verificar Existencia");
            }
        });

        buttonSearchType3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchType = "3";
                editTextParam.setVisibility(View.VISIBLE);
                textViewSearchType.setText("Opción seleccionada: Buscar Repeticiones");
            }
        });
    }

    private void connectToServer() {
        Log.d(TAG, "Attempting to connect to server...");
        tcpClient = new TCPClient("192.168.18.48", 8877, new TCPClient.OnMessageReceived() {
            @Override
            public void messageReceived(String message) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // Mantener el mensaje anterior y añadir el nuevo mensaje
                        textViewStatus.append(message + "\n");
                        Log.d(TAG, "Message from server: " + message);
                    }
                });
            }
        });
        tcpClient.connect();
        textViewConnectionStatus.setText("Estado de conexión: Conectado");
    }

    private void checkPermissionAndOpenFilePicker() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_PERMISSION);
        } else {
            openFilePicker();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openFilePicker();
            } else {
                Toast.makeText(this, "Permiso denegado", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("text/plain");
        startActivityForResult(intent, PICK_TXT_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_TXT_FILE && resultCode == RESULT_OK) {
            if (data != null) {
                fileUri = data.getData();
                if (fileUri != null) {
                    fileName = fileUri.getLastPathSegment();
                    textViewFileName.setText("Archivo seleccionado: " + fileName);
                }
            }
        }
    }

    private void searchKeyword() {
        String keyword = editTextKeyword.getText().toString();
        String param = editTextParam.getText().toString();
        if (keyword.isEmpty()) {
            Toast.makeText(this, "Por favor ingrese una palabra clave", Toast.LENGTH_SHORT).show();
            return;
        }

        new SearchKeywordTask().execute(keyword, param);
    }

    private class SearchKeywordTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            String keyword = params[0];
            String param = params[1];
            try {
                if (tcpClient == null || !tcpClient.isConnected()) {
                    return "Error: No hay conexión al servidor";
                }

                tcpClient.sendMessage(searchType); // Enviar el tipo de búsqueda
                tcpClient.sendMessage(keyword); // Enviar la palabra clave
                tcpClient.sendMessage(param); // Enviar el parámetro extra

                Log.d(TAG, "Sent search type: " + searchType);
                Log.d(TAG, "Sent keyword: " + keyword);
                Log.d(TAG, "Sent parameter: " + param);

                // Leer y enviar el contenido del archivo
                BufferedReader fileReader = new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(fileUri)));
                String line;
                while ((line = fileReader.readLine()) != null) {
                    tcpClient.sendMessage(line);
                    Log.d(TAG, "Sending line: " + line);
                }
                fileReader.close();

                tcpClient.sendMessage(""); // Enviar una línea vacía para indicar el final del archivo
                Log.d(TAG, "Finished sending file");

                // Leer respuesta del servidor
                StringBuilder response = new StringBuilder();
                BufferedReader input = tcpClient.getInput();
                while ((line = input.readLine()) != null) {
                    response.append(line).append("\n");
                    Log.d(TAG, "Received line: " + line);
                }

                return response.toString();
            } catch (Exception e) {
                Log.e(TAG, "Error en la búsqueda de la palabra clave: " + e.getMessage(), e);
                return "Error: " + e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String result) {
            textViewStatus.append(result); // Añadir el resultado al TextView sin sobrescribirlo
        }
    }
}
