package com.dailytask;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.PersistentCacheSettings;

public class LoginActivity extends AppCompatActivity {

    private EditText etLoginName, etLoginEmail, etLoginPassword, etLoginConfirmPassword;
    private Button btnMainAction;
    private TextView tvLoginSubtitle, tvSwitchMode;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private boolean isLoginMode = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                .build();
        db.setFirestoreSettings(settings);

        if (mAuth.getCurrentUser() != null) {
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        etLoginName = findViewById(R.id.etLoginName);
        etLoginEmail = findViewById(R.id.etLoginEmail);
        etLoginPassword = findViewById(R.id.etLoginPassword);
        etLoginConfirmPassword = findViewById(R.id.etLoginConfirmPassword);
        btnMainAction = findViewById(R.id.btnMainAction);
        tvLoginSubtitle = findViewById(R.id.tvLoginSubtitle);
        tvSwitchMode = findViewById(R.id.tvSwitchMode);

        tvSwitchMode.setOnClickListener(v -> {
            isLoginMode = !isLoginMode;
            if (isLoginMode) {
                tvLoginSubtitle.setText("Smart Family Task Collaboration System");
                etLoginName.setVisibility(View.GONE);
                etLoginConfirmPassword.setVisibility(View.GONE);
                btnMainAction.setText("Secure Login");
                btnMainAction.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#2196F3")));
                tvSwitchMode.setText("Don\'t have an account? Tap here to Register");
            } else {
                tvLoginSubtitle.setText("Create New Family Member Account");
                etLoginName.setVisibility(View.VISIBLE);
                etLoginConfirmPassword.setVisibility(View.VISIBLE);
                btnMainAction.setText("Register Now");
                btnMainAction.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#4CAF50")));
                tvSwitchMode.setText("Already have an account? Tap here to Login");
            }
        });

        btnMainAction.setOnClickListener(v -> {
            String email = etLoginEmail.getText().toString().trim();
            String password = etLoginPassword.getText().toString().trim();
            String confirmPassword = etLoginConfirmPassword.getText().toString().trim();
            String name = etLoginName.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(LoginActivity.this, getString(R.string.toast_login_empty_error), Toast.LENGTH_SHORT).show();
                return;
            }
            if (password.length() < 6) {
                Toast.makeText(LoginActivity.this, getString(R.string.toast_login_length_error), Toast.LENGTH_SHORT).show();
                return;
            }

            if (isLoginMode) {
                mAuth.signInWithEmailAndPassword(email, password)
                        .addOnSuccessListener(authResult -> {
                            Toast.makeText(LoginActivity.this, getString(R.string.toast_login_success), Toast.LENGTH_SHORT).show();
                            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                            startActivity(intent);
                            finish();
                        })
                        .addOnFailureListener(e -> Toast.makeText(LoginActivity.this, getString(R.string.toast_login_failed, e.getMessage()), Toast.LENGTH_LONG).show());
            } else {
                if (name.isEmpty()) {
                    Toast.makeText(LoginActivity.this, getString(R.string.toast_reg_name_empty), Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!password.equals(confirmPassword)) {
                    Toast.makeText(LoginActivity.this, getString(R.string.toast_password_mismatch), Toast.LENGTH_LONG).show();
                    return;
                }

                mAuth.createUserWithEmailAndPassword(email, password)
                        .addOnSuccessListener(authResult -> {
                            Toast.makeText(LoginActivity.this, getString(R.string.toast_reg_success), Toast.LENGTH_SHORT).show();
                            Intent intent = new Intent(LoginActivity.this, GroupSetupActivity.class);
                            intent.putExtra("USER_NAME", name);
                            startActivity(intent);
                            finish();
                        })
                        .addOnFailureListener(e -> Toast.makeText(LoginActivity.this, getString(R.string.toast_reg_failed, e.getMessage()), Toast.LENGTH_LONG).show());
            }
        });
    }
}