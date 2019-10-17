package me.proxer.app.tv.presenters;

import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.Button;

import androidx.leanback.widget.Action;
import androidx.leanback.widget.Presenter;

import me.proxer.app.R;

/**
 * @author Graphicscore (Dominik Louven)
 */
class ActionViewHolder extends Presenter.ViewHolder {
    Action mAction;
    Button mButton;
    int mLayoutDirection;

    public ActionViewHolder(View view, int layoutDirection) {
        super(view);
        mButton = (Button) view.findViewById(R.id.lb_action_button);
        mLayoutDirection = layoutDirection;
    }
}

abstract class ActionPresenter extends Presenter {
    @Override
    public void onBindViewHolder(Presenter.ViewHolder viewHolder, Object item) {
        Action action = (Action) item;
        ActionViewHolder vh = (ActionViewHolder) viewHolder;
        vh.mAction = action;
        Drawable icon = action.getIcon();
        if (icon != null) {
            final int startPadding = vh.view.getResources()
                    .getDimensionPixelSize(R.dimen.lb_action_with_icon_padding_start);
            final int endPadding = vh.view.getResources()
                    .getDimensionPixelSize(R.dimen.lb_action_with_icon_padding_end);
            vh.view.setPaddingRelative(startPadding, 0, endPadding, 0);
        } else {
            final int padding = vh.view.getResources()
                    .getDimensionPixelSize(R.dimen.lb_action_padding_horizontal);
            vh.view.setPaddingRelative(padding, 0, padding, 0);
        }
        if (vh.mLayoutDirection == View.LAYOUT_DIRECTION_RTL) {
            vh.mButton.setCompoundDrawablesWithIntrinsicBounds(null, null, icon, null);
        } else {
            vh.mButton.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
        }
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
        ActionViewHolder vh = (ActionViewHolder) viewHolder;
        vh.mButton.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
        vh.view.setPadding(0, 0, 0, 0);
        vh.mAction = null;
    }
}
