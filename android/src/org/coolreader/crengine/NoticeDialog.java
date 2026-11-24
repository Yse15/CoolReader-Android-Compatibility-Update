/*
 * CoolReader for Android
 * Copyright (C) 2012 Vadim Lopatin <coolreader.org@gmail.com>
 * Copyright (C) 2021 Aleksey Chernov <valexlin@gmail.com>
 *
 * This program is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.coolreader.crengine;

import android.app.AlertDialog;
import android.os.Bundle;

import org.coolreader.R;

public class NoticeDialog extends AlertDialog {

	private final BaseActivity mActivity;
	private final String mButton1Text;
	private final String mButton2Text;
	private final Runnable mButton1Runnable;
	private final Runnable mButton2Runnable;
	private final Runnable mDismissRunnable;
	private String mMessageText;

	public NoticeDialog(BaseActivity activity, final Runnable onOkButton) {
		this(activity, R.string.dlg_button_ok, onOkButton, R.string.dlg_button_cancel, null, null);
	}

	public NoticeDialog(BaseActivity activity, final Runnable onOkButton, final Runnable onCancelButton) {
		this(activity, R.string.dlg_button_ok, onOkButton, R.string.dlg_button_cancel, onCancelButton, null);
	}

	public NoticeDialog(BaseActivity activity, int button1TextRes, final Runnable button1Runnable, int button2TextRes, final Runnable button2Runnable, final Runnable dismissRunnable) {
		super(activity);
		mActivity = activity;
		mButton1Text = activity.getString(button1TextRes);
		mButton1Runnable = button1Runnable;
		mButton2Text = button2TextRes != 0 ? activity.getString(button2TextRes) : null;
		mButton2Runnable = button2Runnable;
		mDismissRunnable = dismissRunnable;
		setCancelable(false);
		setCanceledOnTouchOutside(false);
		if (null != dismissRunnable)
			setOnDismissListener(dialog -> dismissRunnable.run());
	}

	public void setMessage(int resourceId) {
		mMessageText = mActivity.getString(resourceId);
		super.setMessage(mMessageText);
	}

	@Override
	public void setMessage(CharSequence message) {
		mMessageText = message != null ? message.toString() : "";
		super.setMessage(message);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		setTitle(R.string.app_name);
		if (mMessageText != null && mMessageText.length() > 0)
			super.setMessage(mMessageText);
		setButton(BUTTON_POSITIVE, mButton1Text, (dialog, which) -> {
			if (mButton1Runnable != null)
				mButton1Runnable.run();
		});
		if (mButton2Text != null && mButton2Runnable != null) {
			setButton(BUTTON_NEGATIVE, mButton2Text, (dialog, which) -> {
				mButton2Runnable.run();
			});
		}
		super.onCreate(savedInstanceState);
	}
}
