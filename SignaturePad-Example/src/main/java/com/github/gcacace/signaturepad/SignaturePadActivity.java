package com.github.gcacace.signaturepad;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;

import com.github.gcacace.signaturepad.views.SignaturePad;
import com.github.gcacace.signaturepad.views.SketchBoard;

import it.gcacace.signaturepad.R;

public class SignaturePadActivity extends AppCompatActivity {

    private SignaturePad mSignaturePad;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signature_pad);
        mSignaturePad = (SignaturePad) findViewById(R.id.signature_pad);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        SketchBoard.Mode mode = mSignaturePad.getMode();
        int menuRes = mode.equals(SketchBoard.Mode.DRAW) ?
                R.menu.signature_pad_drawing : R.menu.signature_pad_erasing;
        getMenuInflater().inflate(menuRes, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.action_draw:
                mSignaturePad.setMode(SketchBoard.Mode.DRAW);
                invalidateOptionsMenu();
                break;
            case R.id.action_erase:
                mSignaturePad.setMode(SketchBoard.Mode.ERASE);
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
