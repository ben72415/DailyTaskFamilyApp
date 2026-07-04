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
    private ImageView ivUserIcon;

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
            getSupportActionBar().setTitle(getString(R.string.title_settings_center));
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
            tvSettingsEmail.setText(getString(R.string.settings_email_prefix, mAuth.getCurrentUser().getEmail()));
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
            Toast.makeText(SettingsActivity.this, getString(R.string.toast_logout_success), Toast.LENGTH_SHORT).show();
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
                String userIconStr = documentSnapshot.getString("user_icon");

                tvSettingsName.setText(getString(R.string.settings_name_prefix, name, myRole));
                tvSettingsGroupCode.setText(getString(R.string.settings_group_code_prefix, myGroupId));

                if (ivUserIcon != null && userIconStr != null && !userIconStr.isEmpty()) {
                    try {
                        getContentResolver().takePersistableUriPermission(Uri.parse(userIconStr), Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        ivUserIcon.setImageURI(Uri.parse(userIconStr));
                    } catch (Exception e) {
                        ivUserIcon.setImageResource(android.R.drawable.ic_menu_gallery);
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
                        .addOnSuccessListener(aVoid -> Toast.makeText(SettingsActivity.this, getString(R.string.toast_icon_upload_success), Toast.LENGTH_SHORT).show());
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
                .setTitle(getString(R.string.dialog_regen_code_title))
                .setMessage(getString(R.string.dialog_regen_code_msg))
                .setPositiveButton(getString(R.string.action_confirm), (dialog, which) -> {
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
                                    Toast.makeText(SettingsActivity.this, getString(R.string.toast_invite_code_migrated), Toast.LENGTH_SHORT).show();
                                    myGroupId = newCode;
                                    fetchMyProfileAndGroup();
                                }).addOnFailureListener(e -> {
                                    Toast.makeText(SettingsActivity.this, getString(R.string.toast_invite_code_failed, e.getMessage()), Toast.LENGTH_LONG).show();
                                });
                            })
                            .addOnFailureListener(e -> {
                                batch.commit().addOnSuccessListener(aVoid -> {
                                    Toast.makeText(SettingsActivity.this, getString(R.string.toast_invite_code_migration_error), Toast.LENGTH_SHORT).show();
                                    myGroupId = newCode;
                                    fetchMyProfileAndGroup();
                                });
                            });
                })
                .setNegativeButton(getString(R.string.action_cancel), null).show();
    }

    private void handleLeaveGroupLogic() {
        int adminCount = 0;
        for (DocumentSnapshot doc : memberDocList) {
            if ("Admin".equals(doc.getString("role"))) {
                adminCount++;
            }
        }

        if ("Admin".equals(myRole) && adminCount <= 1 && memberDocList.size() > 1) {
            Toast.makeText(this, getString(R.string.toast_admin_leave_denied), Toast.LENGTH_LONG).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_leave_group_title))
                .setMessage(getString(R.string.dialog_leave_group_msg))
                .setPositiveButton(getString(R.string.action_confirm), (dialog, which) -> {
                    if (memberDocList.size() <= 1) {
                        db.collection("groups").document(myGroupId).delete();
                    }

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("group_id", "");
                    updates.put("role", "User");

                    db.collection("users").document(currentUid).update(updates)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(SettingsActivity.this, getString(R.string.toast_leave_group_success), Toast.LENGTH_LONG).show();
                                Intent intent = new Intent(SettingsActivity.this, GroupSetupActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();
                            });
                })
                .setNegativeButton(getString(R.string.action_cancel), null).show();
    }

    private void popupChangePasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.dialog_change_pwd_title));

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(60, 40, 60, 20);

        final EditText etNewPassword = new EditText(this);
        etNewPassword.setHint(getString(R.string.dialog_change_pwd_hint));
        etNewPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(etNewPassword);

        final EditText etConfirmPassword = new EditText(this);
        etConfirmPassword.setHint(getString(R.string.dialog_change_pwd_confirm_hint));
        etConfirmPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 30, 0, 0);
        etConfirmPassword.setLayoutParams(params);
        layout.addView(etConfirmPassword);

        builder.setView(layout);

        builder.setPositiveButton(getString(R.string.action_confirm), (dialog, which) -> {
            String newPassword = etNewPassword.getText().toString().trim();
            String confirmPassword = etConfirmPassword.getText().toString().trim();

            if (newPassword.length() < 6) {
                Toast.makeText(SettingsActivity.this, getString(R.string.toast_password_length_error), Toast.LENGTH_SHORT).show();
                return;
            }

            if (!newPassword.equals(confirmPassword)) {
                Toast.makeText(SettingsActivity.this, getString(R.string.toast_password_mismatch), Toast.LENGTH_LONG).show();
                return;
            }

            if (mAuth.getCurrentUser() != null) {
                mAuth.getCurrentUser().updatePassword(newPassword).addOnSuccessListener(aVoid -> {
                    Toast.makeText(SettingsActivity.this, getString(R.string.toast_password_update_success), Toast.LENGTH_SHORT).show();
                }).addOnFailureListener(e -> Toast.makeText(SettingsActivity.this, getString(R.string.toast_password_update_failed, e.getMessage()), Toast.LENGTH_LONG).show());
            }
        });
        builder.setNegativeButton(getString(R.string.action_cancel), null);
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
            holder.tvMemberRole.setText(getString(R.string.member_role_prefix, role));

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
                        Toast.makeText(SettingsActivity.this, getString(R.string.toast_admin_toggle_denied), Toast.LENGTH_LONG).show();
                        return;
                    }
                }

                db.collection("users").document(uId).update("role", targetNewRole)
                        .addOnSuccessListener(aVoid -> Toast.makeText(SettingsActivity.this, getString(R.string.toast_admin_toggle_success, name, targetNewRole), Toast.LENGTH_SHORT).show());
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