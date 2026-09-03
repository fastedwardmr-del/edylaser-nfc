package com.edylaser3d.nfcwriter;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Color;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.net.URI;
import java.util.Arrays;

public class MainActivity extends Activity {
    private NfcAdapter nfcAdapter;
    private PendingIntent pendingIntent;
    private TextView status;
    private TextView urlView;
    private TextView countView;
    private String pendingUrl;
    private int count = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        status = findViewById(R.id.txtStatus);
        urlView = findViewById(R.id.txtUrl);
        countView = findViewById(R.id.txtCount);
        Button scan = findViewById(R.id.btnScan);
        Button cancel = findViewById(R.id.btnCancel);

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        Intent intent = new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);

        if (nfcAdapter == null) {
            setError("Este teléfono no tiene NFC.");
            scan.setEnabled(false);
        } else if (!nfcAdapter.isEnabled()) {
            setError("Activa el NFC del teléfono para continuar.");
            status.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NFC_SETTINGS)));
        }

        scan.setOnClickListener(v -> startQrScanner());
        cancel.setOnClickListener(v -> resetForNext());
    }

    private void startQrScanner() {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
        integrator.setPrompt("Enfoca el QR de la placa");
        integrator.setBeepEnabled(true);
        integrator.setOrientationLocked(true);
        integrator.initiateScan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            if (result.getContents() != null) acceptQr(result.getContents().trim());
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void acceptQr(String value) {
        if (!isAllowedEdylaserUrl(value)) {
            setError("QR rechazado: no corresponde a una URL de EDYLASER3D.");
            urlView.setText(value);
            return;
        }
        pendingUrl = value;
        status.setText("2. ACERCA AHORA LA PLACA NFC");
        status.setTextColor(Color.rgb(22, 160, 133));
        urlView.setText(value);
    }

    private boolean isAllowedEdylaserUrl(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme()) && host != null &&
                    (host.equalsIgnoreCase("edylaser3d.com") ||
                     host.toLowerCase().endsWith(".edylaser3d.com"));
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nfcAdapter != null) nfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfcAdapter != null) nfcAdapter.disableForegroundDispatch(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Forma compatible desde Android 8; evita depender de la sobrecarga añadida en Android 13.
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        if (tag == null) return;
        if (pendingUrl == null) {
            setError("Primero debes escanear el QR.");
            return;
        }
        writeAndVerify(tag, pendingUrl);
    }

    private void writeAndVerify(Tag tag, String url) {
        NdefMessage message = new NdefMessage(new NdefRecord[]{NdefRecord.createUri(url)});
        try {
            Ndef ndef = Ndef.get(tag);
            if (ndef != null) {
                ndef.connect();
                if (!ndef.isWritable()) throw new Exception("El chip está bloqueado o es de solo lectura.");
                if (message.toByteArray().length > ndef.getMaxSize()) throw new Exception("El chip no tiene espacio suficiente.");
                ndef.writeNdefMessage(message);
                ndef.close();
            } else {
                NdefFormatable formatable = NdefFormatable.get(tag);
                if (formatable == null) throw new Exception("Chip NFC no compatible con formato NDEF.");
                formatable.connect();
                formatable.format(message);
                formatable.close();
            }

            Ndef verify = Ndef.get(tag);
            if (verify != null) {
                verify.connect();
                NdefMessage readBack = verify.getNdefMessage();
                verify.close();
                if (readBack == null || !Arrays.equals(message.toByteArray(), readBack.toByteArray()))
                    throw new Exception("Se escribió, pero la verificación no coincidió. Repite el proceso.");
            }

            count++;
            countView.setText("Placas programadas: " + count);
            status.setText("✓ PLACA PROGRAMADA CORRECTAMENTE");
            status.setTextColor(Color.rgb(0, 128, 72));
            vibrate();
            Toast.makeText(this, "QR y NFC quedaron vinculados", Toast.LENGTH_LONG).show();
            pendingUrl = null;
            findViewById(R.id.btnScan).postDelayed(this::resetForNext, 1800);
        } catch (Exception e) {
            setError("No se pudo grabar: " + e.getMessage());
        }
    }

    private void resetForNext() {
        pendingUrl = null;
        status.setText("1. Escanea el QR de la placa");
        status.setTextColor(Color.rgb(16, 42, 67));
        urlView.setText("Aún no hay un QR cargado");
    }

    private void setError(String message) {
        status.setText("⚠ " + message);
        status.setTextColor(Color.rgb(190, 45, 45));
    }

    private void vibrate() {
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator())
            vibrator.vibrate(VibrationEffect.createOneShot(350, VibrationEffect.DEFAULT_AMPLITUDE));
    }
}
