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

public class LoginActivity extends AppCompatActivity {

    private EditText etLoginName, etLoginEmail, etLoginPassword, etLoginConfirmPassword;
    private Button btnMainAction;
    private TextView tvLoginSubtitle, tvSwitchMode;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db; // 🎯 修正：宣告雲端資料庫變數 db
    private boolean isLoginMode = true; // 標記目前是登入模式還是註冊模式

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();


        db = FirebaseFirestore.getInstance();


        com.google.firebase.firestore.FirebaseFirestoreSettings settings =
                new com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                        .setLocalCacheSettings(com.google.firebase.firestore.PersistentCacheSettings.newBuilder().build())
                        .build();
        db.setFirestoreSettings(settings);

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
                tvLoginSubtitle.setText("智能家庭任務協同系統");
                etLoginName.setVisibility(View.GONE);
                etLoginConfirmPassword.setVisibility(View.GONE);
                btnMainAction.setText("安全登入");
                btnMainAction.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#2196F3")));
                tvSwitchMode.setText("沒有帳號？點擊此處切換至「新增註冊」");
            } else {
                tvLoginSubtitle.setText("建立全新家庭成員帳號");
                etLoginName.setVisibility(View.VISIBLE);
                etLoginConfirmPassword.setVisibility(View.VISIBLE);
                btnMainAction.setText("立即註冊");
                btnMainAction.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#4CAF50")));
                tvSwitchMode.setText("已有帳號？點擊此處切換至「返回登入」");
            }
        });

        btnMainAction.setOnClickListener(v -> {
            String email = etLoginEmail.getText().toString().trim();
            String password = etLoginPassword.getText().toString().trim();
            String confirmPassword = etLoginConfirmPassword.getText().toString().trim();
            String name = etLoginName.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(LoginActivity.this, "請填寫 Email 與密碼！", Toast.LENGTH_SHORT).show();
                return;
            }
            if (password.length() < 6) {
                Toast.makeText(LoginActivity.this, "密碼長度至少需要 6 位數！", Toast.LENGTH_SHORT).show();
                return;
            }

            if (isLoginMode) {
                mAuth.signInWithEmailAndPassword(email, password)
                        .addOnSuccessListener(authResult -> {
                            Toast.makeText(LoginActivity.this, "登入成功！歡迎回來", Toast.LENGTH_SHORT).show();
                            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                            startActivity(intent);
                            finish();
                        })
                        .addOnFailureListener(e -> Toast.makeText(LoginActivity.this, "登入失敗: " + e.getMessage(), Toast.LENGTH_LONG).show());
            } else {

                if (name.isEmpty()) {
                    Toast.makeText(LoginActivity.this, "註冊必須填寫用戶姓名！", Toast.LENGTH_SHORT).show();
                    return;
                }


                if (!password.equals(confirmPassword)) {
                    Toast.makeText(LoginActivity.this, "❌ 兩次輸入的密碼不相同，請重新核對！", Toast.LENGTH_LONG).show();
                    return;
                }

                mAuth.createUserWithEmailAndPassword(email, password)
                        .addOnSuccessListener(authResult -> {
                            Toast.makeText(LoginActivity.this, "帳號建立成功！", Toast.LENGTH_SHORT).show();
                            Intent intent = new Intent(LoginActivity.this, GroupSetupActivity.class);
                            intent.putExtra("USER_NAME", name);
                            startActivity(intent);
                            finish();
                        })
                        .addOnFailureListener(e -> Toast.makeText(LoginActivity.this, "註冊失敗: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }
}