package com.dailytask;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.List;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {

    private List<Task> taskList;
    private FirebaseFirestore db;
    private String currentUid;

    public TaskAdapter(List<Task> taskList) {
        this.taskList = taskList;
        db = FirebaseFirestore.getInstance();
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        } else {
            currentUid = "";
        }
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_task, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        Task task = taskList.get(position);
        String status = task.getStatus() != null ? task.getStatus() : "Pending";
        String time = task.getTime() != null ? task.getTime() : "12:00 - 13:00";
        String title = task.getTitle() != null ? task.getTitle() : holder.itemView.getContext().getString(R.string.untitled_task);
        String member = task.getMember() != null ? task.getMember() : holder.itemView.getContext().getString(R.string.unknown_member);
        String createdBy = task.getCreatedBy() != null ? task.getCreatedBy() : "";
        boolean hasImages = task.getImage() != null && !task.getImage().isEmpty();
        String attachmentIcon = hasImages ? " 📎" : "";

        // 轉換為英文狀態標籤
        String statusLabel = status.equals("Completed") ? "[Completed] ✅ " :
                (status.equals("Rescheduled") ? "[Rescheduled] 🔁 " : "[Pending] ⏳ ");

        holder.tvTaskTitle.setText(statusLabel + "[" + time + "] " + title + attachmentIcon);
        holder.tvTaskMember.setText("Assignee: " + member);

        holder.viewMemberTag.setImageResource(android.R.drawable.ic_menu_gallery);
        if (!createdBy.isEmpty()) {
            db.collection("users").document(createdBy).get().addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    String userIconStr = documentSnapshot.getString("user_icon");
                    if (userIconStr != null && !userIconStr.isEmpty() && holder.viewMemberTag != null) {
                        try {
                            holder.itemView.getContext().getContentResolver().takePersistableUriPermission(
                                    Uri.parse(userIconStr), Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            holder.viewMemberTag.setImageURI(Uri.parse(userIconStr));
                        } catch (Exception e) {
                            holder.viewMemberTag.setImageResource(android.R.drawable.ic_menu_gallery);
                        }
                    }
                }
            });
        }

        holder.itemView.setOnClickListener(v -> {
            Context context = v.getContext();
            Intent intent = new Intent(context, EditTaskActivity.class);
            intent.putExtra("TASK_ID", task.getId());
            intent.putExtra("TASK_TITLE", title);
            intent.putExtra("TASK_MEMBER", member);
            intent.putExtra("TASK_DATE", task.getDate() != null ? task.getDate() : "");
            intent.putExtra("TASK_TIME", time);
            intent.putExtra("TASK_NOTES", task.getNotes() != null ? task.getNotes() : "");
            intent.putExtra("TASK_STATUS", status);
            intent.putExtra("TASK_IMAGE", task.getImage() != null ? task.getImage() : "");
            intent.putExtra("CLOUD_DOC_ID", task.getCloudDocId() != null ? task.getCloudDocId() : "");
            intent.putExtra("CREATED_BY_UID", createdBy);
            context.startActivity(intent);
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (currentUid == null || currentUid.isEmpty()) return true;
            Context ctx = v.getContext();
            db.collection("users").document(currentUid).get().addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    String myRole = documentSnapshot.getString("role");
                    if ("User".equals(myRole) && !currentUid.equals(createdBy)) {
                        Toast.makeText(ctx, ctx.getString(R.string.toast_privilege_insufficient), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    new AlertDialog.Builder(ctx)
                            .setTitle(ctx.getString(R.string.dialog_delete_task_title))
                            .setMessage(ctx.getString(R.string.dialog_delete_task_msg, title))
                            .setPositiveButton(ctx.getString(R.string.action_confirm), (dialog, which) -> {
                                if (task.getCloudDocId() != null) {
                                    db.collection("tasks").document(task.getCloudDocId()).delete()
                                            .addOnSuccessListener(aVoid -> Toast.makeText(ctx, ctx.getString(R.string.toast_cloud_delete_success), Toast.LENGTH_SHORT).show());
                                }
                            })
                            .setNegativeButton(ctx.getString(R.string.action_cancel), null).show();
                }
            });
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return taskList.size();
    }

    static class TaskViewHolder extends RecyclerView.ViewHolder {
        ImageView viewMemberTag;
        TextView tvTaskTitle, tvTaskMember;
        public TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            viewMemberTag = itemView.findViewById(R.id.viewMemberTag);
            tvTaskTitle = itemView.findViewById(R.id.tvTaskTitle);
            tvTaskMember = itemView.findViewById(R.id.tvTaskMember);
        }
    }
}