package me.proxer.app.tv.presenters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.leanback.widget.Action;

import me.proxer.app.R;

/**
 * @author Graphicscore (Dominik Louven)
 */
public class OneLineActionPresenter extends ActionPresenter {
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.action_black, parent, false);
        return new ActionViewHolder(v, parent.getLayoutDirection());
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        super.onBindViewHolder(viewHolder, item);
        ActionViewHolder vh = ((ActionViewHolder) viewHolder);
        Action action = (Action) item;
        vh.mButton.setText(action.getLabel1());
    }
}

