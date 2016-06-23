package com.github.gcacace.signaturepad;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;

import com.github.gcacace.signaturepad.views.SignaturePad;

import it.gcacace.signaturepad.R;

public class SignaturePadActivity extends AppCompatActivity {

    private SignaturePad mSignaturePad;
    private ProgressDialog restoreSpinner = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signature_pad);

        mSignaturePad = (SignaturePad) findViewById(R.id.signature_pad);
        mSignaturePad.setOnRestoreListener(new SignaturePad.OnRestoreListener() {
            @Override
            public void onRestoreFromHistory() {
                restoreSpinner = new ProgressDialog(SignaturePadActivity.this);
                restoreSpinner.setCanceledOnTouchOutside(false);
                restoreSpinner.setCancelable(true);
                restoreSpinner.setIndeterminate(true);
                restoreSpinner.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                restoreSpinner.setMessage("Path calculating……");
                restoreSpinner.show();
            }

            @Override
            public void onSignatureRestored() {
                restoreSpinner.dismiss();
                restoreSpinner = null;
            }
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        SignaturePad.SignatureMode mode = mSignaturePad.getMode();
        int menuRes = mode.equals(SignaturePad.SignatureMode.DRAW) ?
                R.menu.signature_pad_drawing : R.menu.signature_pad_erasing;
        getMenuInflater().inflate(menuRes, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.action_draw:
                mSignaturePad.setMode(SignaturePad.SignatureMode.DRAW);
                invalidateOptionsMenu();
                break;
            case R.id.action_erase:
                mSignaturePad.setMode(SignaturePad.SignatureMode.ERASE);
                invalidateOptionsMenu();
                break;
            case R.id.action_undo:
                mSignaturePad.undo();
                break;
            case R.id.action_redo:
                mSignaturePad.redo();
                break;
            case R.id.action_clear:
                mSignaturePad.clear();
                break;
        }
        return super.onOptionsItemSelected(item);
    }
}
