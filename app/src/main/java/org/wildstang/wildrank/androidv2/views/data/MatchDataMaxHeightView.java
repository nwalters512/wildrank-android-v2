package org.wildstang.wildrank.androidv2.views.data;

import android.content.Context;
import android.util.AttributeSet;

import com.couchbase.lite.Document;

import org.wildstang.wildrank.androidv2.interfaces.IMatchDataView;
import org.wildstang.wildrank.androidv2.models.StackModel;

import java.util.List;
import java.util.Map;

import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.observables.MathObservable;
import rx.schedulers.Schedulers;

/**
 * Created by Nathan on 3/18/2015.
 */
public class MatchDataMaxHeightView extends MatchDataView implements IMatchDataView {

    public MatchDataMaxHeightView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void calculateFromDocuments(List<Document> documents) {
        if (documents == null) {
            return;
        } else if (documents.size() == 0) {
            return;
        }
        /*
        int maxHeight = 0;
        for (Document document : documents) {
            Map<String, Object> data = (Map<String, Object>) document.getProperty("data");
            List<Map<String, Object>> stacks = (List<Map<String, Object>>) data.get("stacks");
            for (Map<String, Object> stack : stacks) {
                boolean preexisting = (boolean) stack.get(StackModel.PREEXISTING_KEY);
                int preexistingHeight = (int) stack.get(StackModel.PREEXISTING_HEIGHT_KEY);
                int toteCount = (int) stack.get(StackModel.TOTE_COUNT_KEY);
                int height;
                if (preexisting) {
                    height = preexistingHeight + toteCount;
                } else {
                    height = toteCount;
                }
                maxHeight = Math.max(maxHeight, height);
            }
        }
        setValueText("" + maxHeight);*/

        Observable heightsObservable = Observable.from(documents)
                .map(doc -> (Map<String, Object>) doc.getProperty("data"))
                .flatMap(data -> Observable.from((List<Map<String, Object>>) data.get("stacks")))
                .map(stack -> {
                    boolean preexisting = (boolean) stack.get(StackModel.PREEXISTING_KEY);
                    int preexistingHeight = (int) stack.get(StackModel.PREEXISTING_HEIGHT_KEY);
                    int toteCount = (int) stack.get(StackModel.TOTE_COUNT_KEY);
                    int height;
                    if (preexisting) {
                        height = preexistingHeight + toteCount;
                    } else {
                        height = toteCount;
                    }
                    return height;
                }).subscribeOn(Schedulers.computation());

        MathObservable.max(heightsObservable).observeOn(AndroidSchedulers.mainThread())
                .subscribe(max -> setValueText("" + max));
    }
}