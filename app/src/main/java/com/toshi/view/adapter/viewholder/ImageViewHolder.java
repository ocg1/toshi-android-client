/*
 * 	Copyright (c) 2017. Toshi Inc
 *
 * 	This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.toshi.view.adapter.viewholder;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.makeramen.roundedimageview.RoundedImageView;
import com.toshi.R;
import com.toshi.model.local.SendState;
import com.toshi.model.sofa.SofaMessage;
import com.toshi.util.ImageUtil;
import com.toshi.view.adapter.listeners.OnItemClickListener;

import java.io.File;

import de.hdodenhof.circleimageview.CircleImageView;

public final class ImageViewHolder extends RecyclerView.ViewHolder {

    private @Nullable CircleImageView avatar;
    private @Nullable ImageView sentStatus;
    private @Nullable TextView errorMessage;
    private @NonNull RoundedImageView image;

    private @SendState.State int sendState;
    private String attachmentFilePath;
    private String avatarUri;

    public ImageViewHolder(final View v) {
        super(v);
        this.avatar = (CircleImageView) v.findViewById(R.id.avatar);
        this.image = (RoundedImageView) v.findViewById(R.id.image);
        this.sentStatus = (ImageView) v.findViewById(R.id.sent_status);
        this.errorMessage = (TextView) v.findViewById(R.id.error_message);
    }

    public ImageViewHolder setAvatarUri(final String uri) {
        this.avatarUri = uri;
        return this;
    }

    public ImageViewHolder setSendState(final @SendState.State int sendStatus) {
        this.sendState = sendStatus;
        return this;
    }

    public ImageViewHolder setAttachmentFilePath(final String filePath) {
        this.attachmentFilePath = filePath;
        return this;
    }

    public ImageViewHolder setOnResendListener(final OnItemClickListener<SofaMessage> listener,
                                              final SofaMessage sofaMessage) {
        if (this.sendState == SendState.STATE_PENDING || this.sendState == SendState.STATE_FAILED) {
            this.itemView.setOnClickListener(v -> listener.onItemClick(sofaMessage));
        }
        return this;
    }

    public ImageViewHolder draw() {
        showImage();
        renderAvatar();
        setSendState();
        return this;
    }

    private void renderAvatar() {
        if (this.avatar == null) return;
        ImageUtil.load(this.avatarUri, this.avatar);
    }

    private void showImage() {
        resetImage();
        final File imageFile = new File(this.attachmentFilePath);
        ImageUtil.renderFileIntoTarget(imageFile, this.image);
        this.attachmentFilePath = null;
    }

    private void resetImage() {
        this.image.layout(0,0,0,0);
    }

    private void setSendState() {
        if (this.sentStatus == null || this.errorMessage == null) return;
        final int visibility = this.sendState == SendState.STATE_FAILED || this.sendState == SendState.STATE_PENDING
                ? View.VISIBLE
                : View.GONE;
        this.sentStatus.setVisibility(visibility);
        this.errorMessage.setVisibility(visibility);
    }

    public ImageViewHolder setClickableImage(final OnItemClickListener<String> listener, final String filePath) {
        this.image.setOnClickListener(v -> listener.onItemClick(filePath));
        return this;
    }
}
