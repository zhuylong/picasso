/*
 * Copyright (C) 2013 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.picasso;

import android.support.annotation.VisibleForTesting;
import android.view.View;
import android.view.View.OnAttachStateChangeListener;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnPreDrawListener;
import android.widget.ImageView;
import java.lang.ref.WeakReference;

class DeferredRequestCreator implements OnPreDrawListener, OnAttachStateChangeListener {
  private final RequestCreator creator;
  @VisibleForTesting final WeakReference<ImageView> target;

  private ViewTreeObserver vto;
  @VisibleForTesting Callback callback;

  DeferredRequestCreator(RequestCreator creator, ImageView target, Callback callback) {
    this.creator = creator;
    this.target = new WeakReference<>(target);
    this.callback = callback;

    target.addOnAttachStateChangeListener(this);

    // Only add the pre-draw listener if the view is already attached.
    if (target.getWindowToken() != null) {
      onViewAttachedToWindow(target);
    }
  }

  @Override public void onViewAttachedToWindow(View view) {
    // Save the VTO to which we're registering so that we can unregister from it directly should
    // the view be detached. This works around a bug where the listener is leaked on detach without
    // a pre-draw event. See: https://github.com/square/picasso/issues/1321
    vto = view.getViewTreeObserver();
    vto.addOnPreDrawListener(DeferredRequestCreator.this);
  }

  @Override public void onViewDetachedFromWindow(View v) {
    vto.removeOnPreDrawListener(DeferredRequestCreator.this);
  }

  @Override public boolean onPreDraw() {
    ImageView target = this.target.get();
    if (target == null) {
      return true;
    }

    if (!vto.isAlive()) {
      return true;
    }

    int width = target.getWidth();
    int height = target.getHeight();

    if (width <= 0 || height <= 0 || target.isLayoutRequested()) {
      return true;
    }

    target.removeOnAttachStateChangeListener(this);
    vto.removeOnPreDrawListener(this);
    this.target.clear();

    this.creator.unfit().resize(width, height).into(target, callback);
    return true;
  }

  void cancel() {
    creator.clearTag();
    callback = null;

    ImageView target = this.target.get();
    if (target == null) {
      return;
    }
    this.target.clear();

    target.removeOnAttachStateChangeListener(this);

    if (!vto.isAlive()) {
      return;
    }
    vto.removeOnPreDrawListener(this);
  }

  Object getTag() {
    return creator.getTag();
  }
}
