/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.quickstep;

import static com.android.launcher3.anim.Interpolators.ACCEL_2;
import static com.android.launcher3.anim.Interpolators.INSTANT;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.quickstep.AbsSwipeUpHandler.RECENTS_ATTACH_DURATION;
import static com.android.quickstep.SysUINavigationMode.getMode;
import static com.android.quickstep.util.RecentsAtomicAnimationFactory.INDEX_RECENTS_FADE_ANIM;
import static com.android.quickstep.util.RecentsAtomicAnimationFactory.INDEX_RECENTS_TRANSLATE_X_ANIM;
import static com.android.quickstep.views.RecentsView.ADJACENT_PAGE_OFFSET;
import static com.android.quickstep.views.RecentsView.FULLSCREEN_PROGRESS;
import static com.android.quickstep.views.RecentsView.RECENTS_SCALE_PROPERTY;
import static com.android.quickstep.views.RecentsView.TASK_SECONDARY_TRANSLATION;

import android.animation.Animator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.statehandlers.DepthController;
import com.android.launcher3.statemanager.BaseState;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.launcher3.taskbar.TaskbarController;
import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.launcher3.util.WindowBounds;
import com.android.quickstep.SysUINavigationMode.Mode;
import com.android.quickstep.util.ActivityInitListener;
import com.android.quickstep.util.AnimatorControllerWithResistance;
import com.android.quickstep.util.SplitScreenBounds;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Utility class which abstracts out the logical differences between Launcher and RecentsActivity.
 */
@TargetApi(Build.VERSION_CODES.P)
public abstract class BaseActivityInterface<STATE_TYPE extends BaseState<STATE_TYPE>,
        ACTIVITY_TYPE extends StatefulActivity<STATE_TYPE>> {

    public final boolean rotationSupportedByActivity;

    private final STATE_TYPE mOverviewState, mBackgroundState;

    protected BaseActivityInterface(boolean rotationSupportedByActivity,
            STATE_TYPE overviewState, STATE_TYPE backgroundState) {
        this.rotationSupportedByActivity = rotationSupportedByActivity;
        mOverviewState = overviewState;
        mBackgroundState = backgroundState;
    }

    public void onTransitionCancelled(boolean activityVisible) {
        ACTIVITY_TYPE activity = getCreatedActivity();
        if (activity == null) {
            return;
        }
        STATE_TYPE startState = activity.getStateManager().getRestState();
        activity.getStateManager().goToState(startState, activityVisible);
    }

    public abstract int getSwipeUpDestinationAndLength(
            DeviceProfile dp, Context context, Rect outRect,
            PagedOrientationHandler orientationHandler);

    public void onSwipeUpToRecentsComplete() {
        // Re apply state in case we did something funky during the transition.
        ACTIVITY_TYPE activity = getCreatedActivity();
        if (activity == null) {
            return;
        }
        activity.getStateManager().reapplyState();
    }

    public abstract void onSwipeUpToHomeComplete(RecentsAnimationDeviceState deviceState);

    public abstract void onAssistantVisibilityChanged(float visibility);

    public abstract AnimationFactory prepareRecentsUI(RecentsAnimationDeviceState deviceState,
            boolean activityVisible, Consumer<AnimatorControllerWithResistance> callback);

    public abstract ActivityInitListener createActivityInitListener(
            Predicate<Boolean> onInitListener);

    /**
     * Sets a callback to be run when an activity launch happens while launcher is not yet resumed.
     */
    public void setOnDeferredActivityLaunchCallback(Runnable r) {}

    @Nullable
    public abstract ACTIVITY_TYPE getCreatedActivity();

    @Nullable
    public DepthController getDepthController() {
        return null;
    }

    @Nullable
    public TaskbarController getTaskbarController() {
        return null;
    }

    public final boolean isResumed() {
        ACTIVITY_TYPE activity = getCreatedActivity();
        return activity != null && activity.hasBeenResumed();
    }

    public final boolean isStarted() {
        ACTIVITY_TYPE activity = getCreatedActivity();
        return activity != null && activity.isStarted();
    }

    @UiThread
    @Nullable
    public abstract <T extends RecentsView> T getVisibleRecentsView();

    @UiThread
    public abstract boolean switchToRecentsIfVisible(Runnable onCompleteCallback);

    public abstract Rect getOverviewWindowBounds(
            Rect homeBounds, RemoteAnimationTargetCompat target);

    public abstract boolean allowMinimizeSplitScreen();

    public boolean deferStartingActivity(RecentsAnimationDeviceState deviceState, MotionEvent ev) {
        return deviceState.isInDeferredGestureRegion(ev);
    }

    /**
     * @return Whether the gesture in progress should be cancelled.
     */
    public boolean shouldCancelCurrentGesture() {
        return false;
    }

    public abstract void onExitOverview(RotationTouchHelper deviceState,
            Runnable exitRunnable);

    public abstract boolean isInLiveTileMode();

    public abstract void onLaunchTaskFailed();

    public void onLaunchTaskSuccess() {
        ACTIVITY_TYPE activity = getCreatedActivity();
        if (activity == null) {
            return;
        }
        activity.getStateManager().moveToRestState();
    }

    public void closeOverlay() { }

    public void switchRunningTaskViewToScreenshot(ThumbnailData thumbnailData, Runnable runnable) {
        ACTIVITY_TYPE activity = getCreatedActivity();
        if (activity == null) {
            return;
        }
        RecentsView recentsView = activity.getOverviewPanel();
        if (recentsView == null) {
            if (runnable != null) {
                runnable.run();
            }
            return;
        }
        recentsView.switchToScreenshot(thumbnailData, runnable);
    }

    /**
     * Calculates the taskView size for the provided device configuration.
     */
    public final void calculateTaskSize(Context context, DeviceProfile dp, Rect outRect,
            PagedOrientationHandler orientedState) {
        Resources res = context.getResources();
        if (dp.isTablet && FeatureFlags.ENABLE_OVERVIEW_GRID.get()) {
            Rect gridRect = new Rect();
            calculateGridSize(context, dp, gridRect);

            int verticalMargin = res.getDimensionPixelSize(
                    R.dimen.overview_grid_focus_vertical_margin);
            float taskHeight = gridRect.height() - verticalMargin * 2;

            PointF taskDimension = getTaskDimension(context, dp);
            float scale = taskHeight / Math.max(taskDimension.x, taskDimension.y);
            int outWidth = Math.round(scale * taskDimension.x);
            int outHeight = Math.round(scale * taskDimension.y);

            int gravity = Gravity.CENTER_VERTICAL;
            gravity |= orientedState.getRecentsRtlSetting(res) ? Gravity.RIGHT : Gravity.LEFT;
            Gravity.apply(gravity, outWidth, outHeight, gridRect, outRect);
        } else {
            int taskMargin = dp.overviewTaskMarginPx;
            int proactiveRowAndMargin;
            if (dp.isVerticalBarLayout()) {
                // In Vertical Bar Layout the proactive row doesn't have its own space, it's inside
                // the actions row.
                proactiveRowAndMargin = 0;
            } else {
                proactiveRowAndMargin = res.getDimensionPixelSize(
                        R.dimen.overview_proactive_row_height)
                        + res.getDimensionPixelSize(R.dimen.overview_proactive_row_bottom_margin);
            }
            calculateTaskSizeInternal(context, dp,
                    dp.overviewTaskThumbnailTopMarginPx,
                    proactiveRowAndMargin + getOverviewActionsHeight(context) + taskMargin,
                    res.getDimensionPixelSize(R.dimen.overview_minimum_next_prev_size) + taskMargin,
                    outRect);
        }
    }

    private void calculateTaskSizeInternal(Context context, DeviceProfile dp,
            int claimedSpaceAbove, int claimedSpaceBelow, int minimumHorizontalPadding,
            Rect outRect) {
        PointF taskDimension = getTaskDimension(context, dp);
        Rect insets = dp.getInsets();

        Rect potentialTaskRect = new Rect(0, 0, dp.widthPx, dp.heightPx);
        potentialTaskRect.inset(insets.left, insets.top, insets.right, insets.bottom);
        potentialTaskRect.inset(
                minimumHorizontalPadding,
                claimedSpaceAbove,
                minimumHorizontalPadding,
                claimedSpaceBelow);

        float scale = Math.min(
                potentialTaskRect.width() / taskDimension.x,
                potentialTaskRect.height() / taskDimension.y);
        int outWidth = Math.round(scale * taskDimension.x);
        int outHeight = Math.round(scale * taskDimension.y);

        Gravity.apply(Gravity.CENTER, outWidth, outHeight, potentialTaskRect, outRect);
    }

    private PointF getTaskDimension(Context context, DeviceProfile dp) {
        PointF dimension = new PointF();
        if (dp.isMultiWindowMode) {
            WindowBounds bounds = SplitScreenBounds.INSTANCE.getSecondaryWindowBounds(context);
            dimension.x = bounds.availableSize.x;
            dimension.y = bounds.availableSize.y;
        } else if (TaskView.CLIP_STATUS_AND_NAV_BARS) {
            dimension.x = dp.availableWidthPx;
            dimension.y = dp.availableHeightPx;
        } else {
            dimension.x = dp.widthPx;
            dimension.y = dp.heightPx;
        }
        return dimension;
    }

    /**
     * Calculates the overview grid size for the provided device configuration.
     */
    public final void calculateGridSize(Context context, DeviceProfile dp, Rect outRect) {
        Resources res = context.getResources();
        int topMargin = res.getDimensionPixelSize(R.dimen.overview_grid_top_margin);
        int bottomMargin = res.getDimensionPixelSize(R.dimen.overview_grid_bottom_margin);
        int sideMargin = res.getDimensionPixelSize(R.dimen.overview_grid_side_margin);

        Rect insets = dp.getInsets();
        outRect.set(0, 0, dp.widthPx, dp.heightPx);
        outRect.inset(Math.max(insets.left, sideMargin), Math.max(insets.top, topMargin),
                Math.max(insets.right, sideMargin), Math.max(insets.bottom, bottomMargin));
    }

    /**
     * Calculates the overview grid non-focused task size for the provided device configuration.
     */
    public final void calculateGridTaskSize(Context context, DeviceProfile dp, Rect outRect,
            PagedOrientationHandler orientedState) {
        Resources res = context.getResources();
        Rect gridRect = new Rect();
        calculateGridSize(context, dp, gridRect);

        int rowSpacing = res.getDimensionPixelSize(R.dimen.overview_grid_row_spacing);
        float rowHeight = (gridRect.height() - rowSpacing) / 2f;

        PointF taskDimension = getTaskDimension(context, dp);
        float scale = (rowHeight - dp.overviewTaskThumbnailTopMarginPx) / Math.max(
                taskDimension.x, taskDimension.y);
        int outWidth = Math.round(scale * taskDimension.x);
        int outHeight = Math.round(scale * taskDimension.y);

        int gravity = Gravity.TOP;
        gravity |= orientedState.getRecentsRtlSetting(res) ? Gravity.RIGHT : Gravity.LEFT;
        gridRect.inset(0, dp.overviewTaskThumbnailTopMarginPx, 0, 0);
        Gravity.apply(gravity, outWidth, outHeight, gridRect, outRect);
    }

    /**
     * Calculates the modal taskView size for the provided device configuration
     */
    public final void calculateModalTaskSize(Context context, DeviceProfile dp, Rect outRect) {
        calculateTaskSizeInternal(
                context, dp,
                dp.overviewTaskMarginPx,
                getOverviewActionsHeight(context) + dp.overviewTaskMarginPx,
                dp.overviewTaskMarginPx,
                outRect);
    }

    /** Gets the space that the overview actions will take, including bottom margin. */
    public final int getOverviewActionsHeight(Context context) {
        Resources res = context.getResources();
        int actionsBottomMargin = 0;
        if (getMode(context) == Mode.THREE_BUTTONS) {
            actionsBottomMargin = res.getDimensionPixelSize(
                    R.dimen.overview_actions_bottom_margin_three_button);
        } else {
            actionsBottomMargin = res.getDimensionPixelSize(
                    R.dimen.overview_actions_bottom_margin_gesture);
        }
        return actionsBottomMargin
                + res.getDimensionPixelSize(R.dimen.overview_actions_height);
    }

    /**
     * Called when the gesture ends and the animation starts towards the given target. No-op by
     * default, but subclasses can override to add an additional animation with the same duration.
     */
    public @Nullable Animator getParallelAnimationToLauncher(
            GestureState.GestureEndTarget endTarget, long duration) {
        return null;
    }

    /**
     * See {@link com.android.systemui.shared.system.QuickStepContract.SystemUiStateFlags}
     * @param systemUiStateFlags The latest SystemUiStateFlags
     */
    public void onSystemUiFlagsChanged(int systemUiStateFlags) {
    }

    /**
     * Returns the expected STATE_TYPE from the provided GestureEndTarget.
     */
    public abstract STATE_TYPE stateFromGestureEndTarget(GestureState.GestureEndTarget endTarget);

    public interface AnimationFactory {

        void createActivityInterface(long transitionLength);

        /**
         * @param attached Whether to show RecentsView alongside the app window. If false, recents
         *                 will be hidden by some property we can animate, e.g. alpha.
         * @param animate Whether to animate recents to/from its new attached state.
         */
        default void setRecentsAttachedToAppWindow(boolean attached, boolean animate) { }

        default boolean isRecentsAttachedToAppWindow() {
            return false;
        }
    }

    class DefaultAnimationFactory implements AnimationFactory {

        protected final ACTIVITY_TYPE mActivity;
        private final STATE_TYPE mStartState;
        private final Consumer<AnimatorControllerWithResistance> mCallback;

        private boolean mIsAttachedToWindow;

        DefaultAnimationFactory(Consumer<AnimatorControllerWithResistance> callback) {
            mCallback = callback;

            mActivity = getCreatedActivity();
            mStartState = mActivity.getStateManager().getState();
        }

        protected ACTIVITY_TYPE initUI() {
            STATE_TYPE resetState = mStartState;
            if (mStartState.shouldDisableRestore()) {
                resetState = mActivity.getStateManager().getRestState();
            }
            mActivity.getStateManager().setRestState(resetState);
            mActivity.getStateManager().goToState(mBackgroundState, false);
            return mActivity;
        }

        @Override
        public void createActivityInterface(long transitionLength) {
            PendingAnimation pa = new PendingAnimation(transitionLength * 2);
            createBackgroundToOverviewAnim(mActivity, pa);
            AnimatorPlaybackController controller = pa.createPlaybackController();
            mActivity.getStateManager().setCurrentUserControlledAnimation(controller);

            // Since we are changing the start position of the UI, reapply the state, at the end
            controller.setEndAction(() -> mActivity.getStateManager().goToState(
                    controller.getInterpolatedProgress() > 0.5 ? mOverviewState : mBackgroundState,
                    false));

            RecentsView recentsView = mActivity.getOverviewPanel();
            AnimatorControllerWithResistance controllerWithResistance =
                    AnimatorControllerWithResistance.createForRecents(controller, mActivity,
                            recentsView.getPagedViewOrientedState(), mActivity.getDeviceProfile(),
                            recentsView, RECENTS_SCALE_PROPERTY, recentsView,
                            TASK_SECONDARY_TRANSLATION);
            mCallback.accept(controllerWithResistance);

            // Creating the activity controller animation sometimes reapplies the launcher state
            // (because we set the animation as the current state animation), so we reapply the
            // attached state here as well to ensure recents is shown/hidden appropriately.
            if (SysUINavigationMode.getMode(mActivity) == Mode.NO_BUTTON) {
                setRecentsAttachedToAppWindow(mIsAttachedToWindow, false);
            }
        }

        @Override
        public void setRecentsAttachedToAppWindow(boolean attached, boolean animate) {
            if (mIsAttachedToWindow == attached && animate) {
                return;
            }
            mIsAttachedToWindow = attached;
            RecentsView recentsView = mActivity.getOverviewPanel();
            Animator fadeAnim = mActivity.getStateManager()
                    .createStateElementAnimation(INDEX_RECENTS_FADE_ANIM, attached ? 1 : 0);

            float fromTranslation = attached ? 1 : 0;
            float toTranslation = attached ? 0 : 1;
            mActivity.getStateManager()
                    .cancelStateElementAnimation(INDEX_RECENTS_TRANSLATE_X_ANIM);
            if (!recentsView.isShown() && animate) {
                ADJACENT_PAGE_OFFSET.set(recentsView, fromTranslation);
            } else {
                fromTranslation = ADJACENT_PAGE_OFFSET.get(recentsView);
            }
            if (!animate) {
                ADJACENT_PAGE_OFFSET.set(recentsView, toTranslation);
            } else {
                mActivity.getStateManager().createStateElementAnimation(
                        INDEX_RECENTS_TRANSLATE_X_ANIM,
                        fromTranslation, toTranslation).start();
            }

            fadeAnim.setInterpolator(attached ? INSTANT : ACCEL_2);
            fadeAnim.setDuration(animate ? RECENTS_ATTACH_DURATION : 0).start();
        }

        @Override
        public boolean isRecentsAttachedToAppWindow() {
            return mIsAttachedToWindow;
        }

        protected void createBackgroundToOverviewAnim(ACTIVITY_TYPE activity, PendingAnimation pa) {
            //  Scale down recents from being full screen to being in overview.
            RecentsView recentsView = activity.getOverviewPanel();
            pa.addFloat(recentsView, RECENTS_SCALE_PROPERTY,
                    recentsView.getMaxScaleForFullScreen(), 1, LINEAR);
            pa.addFloat(recentsView, FULLSCREEN_PROGRESS, 1, 0, LINEAR);
        }
    }

    /** Called when OverviewService is bound to this process */
    void onOverviewServiceBound() {
        // Do nothing
    }
}