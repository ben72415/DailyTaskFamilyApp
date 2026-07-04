package com.dailytask;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class SettingsActivity extends AppCompatActivity {

    private TextView tvSettingsName, tvSettingsEmail, tvSettingsGroupCode;
    private Button btnChangePassword, btnRegenGroupCode, btnLeaveGroup, btnLogout;
    private RecyclerView rvGroupMembers;
    private ImageView ivUserIcon; // 🎯 新增：設定頁面的個人頭像元件

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String currentUid;
    private String myGroupId = "";
    private String myRole = "User";

    private List<DocumentSnapshot> memberDocList = new ArrayList<>();
    private MemberAdapter memberAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUid = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : "";

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("⚙️ 系統設定與權限中心");
        }

        tvSettingsName = findViewById(R.id.tvSettingsName);
        tvSettingsEmail = findViewById(R.id.tvSettingsEmail);
        tvSettingsGroupCode = findViewById(R.id.tvSettingsGroupCode);
        btnChangePassword = findViewById(R.id.btnChangePassword);
        btnRegenGroupCode = findViewById(R.id.btnRegenGroupCode);
        btnLeaveGroup = findViewById(R.id.btnLeaveGroup);
        btnLogout = findViewById(R.id.btnLogout);


        ivUserIcon = findViewById(R.id.ivUserIcon);

        rvGroupMembers = findViewById(R.id.rvGroupMembers);
        rvGroupMembers.setLayoutManager(new LinearLayoutManager(this));

        if (mAuth.getCurrentUser() != null) {
            tvSettingsEmail.setText("Email 帳號：" + mAuth.getCurrentUser().getEmail());
        }

        fetchMyProfileAndGroup();


        if (ivUserIcon != null) {
            ivUserIcon.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                startActivityForResult(intent, 500);
            });
        }

        btnChangePassword.setOnClickListener(v -> popupChangePasswordDialog());
        btnRegenGroupCode.setOnClickListener(v -> popupRegenCodeConfirmDialog());
        btnLeaveGroup.setOnClickListener(v -> handleLeaveGroupLogic());

        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            Toast.makeText(SettingsActivity.this, "已安全登出家庭系統", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(SettingsActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void fetchMyProfileAndGroup() {
        db.collection("users").document(currentUid).get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                String name = documentSnapshot.getString("name");
                myRole = documentSnapshot.getString("role");
                myGroupId = documentSnapshot.getString("group_id");
                String userIconStr = documentSnapshot.getString("user_icon"); // 🎯 讀取頭像

                tvSettingsName.setText("姓名：" + name + " (" + myRole + ")");
                tvSettingsGroupCode.setText("🔑 家庭邀請碼：" + myGroupId);

                // 🎯 渲染本地儲存的個人頭像
                if (ivUserIcon != null && userIconStr != null && !userIconStr.isEmpty()) {
                    try {
                        getContentResolver().takePersistableUriPermission(Uri.parse(userIconStr), Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        ivUserIcon.setImageURI(Uri.parse(userIconStr));
                    } catch (Exception e) {
                        ivUserIcon.setImageResource(android.R.drawable.ic_menu_gallery); // 防禦型預設圖
                    }
                }

                if (!"Admin".equals(myRole)) {
                    btnRegenGroupCode.setEnabled(false);
                    btnRegenGroupCode.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#BDC3C7")));
                } else {
                    btnRegenGroupCode.setEnabled(true);
                    btnRegenGroupCode.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#2980B9")));
                }

                listenGroupMembers();
            }
        });
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 500 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri iconUri = data.getData();
            final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
            try {
                getContentResolver().takePersistableUriPermission(iconUri, takeFlags);
                if (ivUserIcon != null) {
                    ivUserIcon.setImageURI(iconUri);
                }


                db.collection("users").document(currentUid).update("user_icon", iconUri.toString())
                        .addOnSuccessListener(aVoid -> Toast.makeText(SettingsActivity.this, "个人 Icon 更换成功！", Toast.LENGTH_SHORT).show());

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void listenGroupMembers() {
        if (myGroupId == null || myGroupId.isEmpty()) return;

        db.collection("users").whereEqualTo("group_id", myGroupId)
                .addSnapshotListener((value, error) -> {
                    if (value != null) {
                        memberDocList = value.getDocuments();
                        memberAdapter = new MemberAdapter();
                        rvGroupMembers.setAdapter(memberAdapter);
                    }
                });
    }

    private void popupRegenCodeConfirmDialog() {
        new AlertDialog.Builder(this)
                .setTitle("🔄 警告：刷新家庭邀請碼")
                .setMessage("更換邀請碼後，舊的代碼將立即作廢，但目前已在組內的成員與歷史任務會安全遷移。確定要更換嗎？")
                .setPositiveButton("確定變更", (dialog, which) -> {
                    String newCode = generateRandomGroupCode();
                    WriteBatch batch = db.batch();

                    batch.delete(db.collection("groups").document(myGroupId));
                    Map<String, Object> newGroupData = new HashMap<>();
                    newGroupData.put("owner_uid", currentUid);
                    newGroupData.put("group_code", newCode);
                    batch.set(db.collection("groups").document(newCode), newGroupData);

                    for (DocumentSnapshot memberDoc : memberDocList) {
                        batch.update(db.collection("users").document(memberDoc.getId()), "group_id", newCode);
                    }

                    db.collection("tasks").whereEqualTo("group_id", myGroupId).get()
                            .addOnSuccessListener(taskSnapshots -> {
                                for (DocumentSnapshot taskDoc : taskSnapshots) {
                                    batch.update(db.collection("tasks").document(taskDoc.getId()), "group_id", newCode);
                                }
                                batch.commit().addOnSuccessListener(aVoid -> {
                                    Toast.makeText(SettingsActivity.this, "邀請碼變更成功，全體歷史任務已安全同步遷移！", Toast.LENGTH_SHORT).show();
                                    myGroupId = newCode;
                                    fetchMyProfileAndGroup();
                                }).addOnFailureListener(e -> {
                                    Toast.makeText(SettingsActivity.this, "變更失敗: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                });
                            })
                            .addOnFailureListener(e -> {
                                batch.commit().addOnSuccessListener(aVoid -> {
                                    Toast.makeText(SettingsActivity.this, "邀請碼已更換，但任務遷移遇到異常。", Toast.LENGTH_SHORT).show();
                                    myGroupId = newCode;
                                    fetchMyProfileAndGroup();
                                });
                            });
                })
                .setNegativeButton("取消", null).show();
    }

    private void handleLeaveGroupLogic() {
        int adminCount = 0;
        for (DocumentSnapshot doc : memberDocList) {
            if ("Admin".equals(doc.getString("role"))) {
                adminCount++;
            }
        }

        if ("Admin".equals(myRole) && adminCount <= 1 && memberDocList.size() > 1) {
            Toast.makeText(this, "🛑 退出拒絕！你是目前唯一的管理者，請先將其他成員升級為 Admin 才能退出！", Toast.LENGTH_LONG).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("🏃‍♂️ 確定要退出群組？")
                .setMessage("退出群組後，你將無法查看或編輯此家庭的任何工作任務。")
                .setPositiveButton("確定退出", (dialog, which) -> {

                    if (memberDocList.size() <= 1) {
                        db.collection("groups").document(myGroupId).delete();
                    }

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("group_id", "");
                    updates.put("role", "User");

                    db.collection("users").document(currentUid).update(updates)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(SettingsActivity.this, "已成功退出家庭群組！", Toast.LENGTH_LONG).show();
                                Intent intent = new Intent(SettingsActivity.this, GroupSetupActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();
                            });
                })
                .setNegativeButton("取消", null).show();
    }

    private void popupChangePasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🔒 修改安全登入密碼");

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(60, 40, 60, 20);

        final EditText etNewPassword = new EditText(this);
        etNewPassword.setHint("請輸入新密碼 (至少 6 位數)");
        etNewPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(etNewPassword);

        final EditText etConfirmPassword = new EditText(this);
        etConfirmPassword.setHint("請再次輸入新密碼以確認");
        etConfirmPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 30, 0, 0);
        etConfirmPassword.setLayoutParams(params);
        layout.addView(etConfirmPassword);

        builder.setView(layout);

        builder.setPositiveButton("確認修改", (dialog, which) -> {
            String newPassword = etNewPassword.getText().toString().trim();
            String confirmPassword = etConfirmPassword.getText().toString().trim();

            if (newPassword.length() < 6) {
                Toast.makeText(SettingsActivity.this, "❌ 密碼長度不足 6 位！", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!newPassword.equals(confirmPassword)) {
                Toast.makeText(SettingsActivity.this, "❌ 兩次輸入的密碼不一致，請重新填寫！", Toast.LENGTH_LONG).show();
                return;
            }

            if (mAuth.getCurrentUser() != null) {
                mAuth.getCurrentUser().updatePassword(newPassword).addOnSuccessListener(aVoid -> {
                    Toast.makeText(SettingsActivity.this, "✅ 雲端驗證：密碼修改成功！", Toast.LENGTH_SHORT).show();
                }).addOnFailureListener(e -> Toast.makeText(SettingsActivity.this, "修改失敗：" + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private String generateRandomGroupCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        Random rd = new Random();
        while (sb.length() < 6) {
            int index = (int) (rd.nextFloat() * chars.length());
            sb.append(chars.charAt(index));
        }
        return sb.toString();
    }

    private class MemberAdapter extends RecyclerView.Adapter<MemberAdapter.MemberViewHolder> {

        @NonNull
        @Override
        public MemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_member, parent, false);
            return new MemberViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull MemberViewHolder holder, int position) {
            DocumentSnapshot memberDoc = memberDocList.get(position);
            String uId = memberDoc.getId();
            String name = memberDoc.getString("name");
            String role = memberDoc.getString("role");

            holder.tvMemberName.setText(name);
            holder.tvMemberRole.setText("群組身分：" + role);

            if ("User".equals(myRole) || currentUid.equals(uId)) {
                holder.btnToggleRole.setVisibility(View.GONE);
            } else {
                holder.btnToggleRole.setVisibility(View.VISIBLE);
            }

            holder.btnToggleRole.setOnClickListener(v -> {
                String targetNewRole = "Admin".equals(role) ? "User" : "Admin";

                if ("Admin".equals(role) && "User".equals(targetNewRole)) {
                    int adminCount = 0;
                    for (DocumentSnapshot doc : memberDocList) {
                        if ("Admin".equals(doc.getString("role"))) {
                            adminCount++;
                        }
                    }
                    if (adminCount <= 1) {
                        Toast.makeText(SettingsActivity.this, "🛑 變更拒絕！家庭組內必須保持至少一名管理者(Admin)！", Toast.LENGTH_LONG).show();
                        return;
                    }
                }

                db.collection("users").document(uId).update("role", targetNewRole)
                        .addOnSuccessListener(aVoid -> Toast.makeText(SettingsActivity.this, "已成功將 " + name + " 切換為 " + targetNewRole, Toast.LENGTH_SHORT).show());
            });
        }

        @Override
        public int getItemCount() {
            return memberDocList.size();
        }

        class MemberViewHolder extends RecyclerView.ViewHolder {
            TextView tvMemberName, tvMemberRole;
            Button btnToggleRole;

            public MemberViewHolder(@NonNull View itemView) {
                super(itemView);
                tvMemberName = itemView.findViewById(R.id.tvMemberName);
                tvMemberRole = itemView.findViewById(R.id.tvMemberRole);
                btnToggleRole = itemView.findViewById(R.id.btnToggleRole);
            }
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}