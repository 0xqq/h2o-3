package hex.genmodel.algos.gbm;

import hex.genmodel.GenModel;
import hex.genmodel.PredictContributions;
import hex.genmodel.PredictContributionsFactory;
import hex.genmodel.algos.tree.*;
import hex.genmodel.utils.DistributionFamily;

import java.util.ArrayList;
import java.util.List;

import static hex.genmodel.utils.DistributionFamily.*;

/**
 * "Gradient Boosting Machine" MojoModel
 */
public final class GbmMojoModel extends SharedTreeMojoModel implements SharedTreeGraphConverter, PredictContributionsFactory {
    public DistributionFamily _family;
    public double _init_f;

    public GbmMojoModel(String[] columns, String[][] domains, String responseColumn) {
        super(columns, domains, responseColumn);
    }


    /**
     * Corresponds to `hex.tree.gbm.GbmMojoModel.score0()`
     */
    @Override
    public final double[] score0(double[] row, double offset, double[] preds) {
        super.scoreAllTrees(row, preds);
        return unifyPreds(row, offset, preds);
    }

    @Override
    public final double[] unifyPreds(double[] row, double offset, double[] preds) {
        if (_family == bernoulli || _family == quasibinomial || _family == modified_huber) {
            double f = preds[1] + _init_f + offset;
            preds[2] = linkInv(_family,f);
            preds[1] = 1.0 - preds[2];
        } else if (_family == multinomial) {
            if (_nclasses == 2) { // 1-tree optimization for binomial
                preds[1] += _init_f + offset; //offset is not yet allowed, but added here to be future-proof
                preds[2] = -preds[1];
            }
            GenModel.GBM_rescale(preds);
        } else { // Regression
            double f = preds[0] + _init_f + offset;
            preds[0] = linkInv(_family,f);
            return preds;
        }
        if (_balanceClasses)
            GenModel.correctProbabilities(preds, _priorClassDistrib, _modelClassDistrib);
        preds[0] = GenModel.getPrediction(preds, _priorClassDistrib, row, _defaultThreshold);
        return preds;
    }

    /**
     * Calculate inverse link depends on distribution type
     * Be careful if you are changing code here - you have to change in hex.Distribution too
     * @param distribution
     * @param f raw prediction
     * @return calculated inverse link value
     */
    private double linkInv(DistributionFamily distribution, double f){
        switch (distribution) {
            case bernoulli:
            case quasibinomial:
            case modified_huber:
            case ordinal:
                return 1/(1+Math.min(1e19, Math.exp(-f)));
            case multinomial:
            case poisson:
            case gamma:
            case tweedie:
                return Math.min(1e19, Math.exp(f));
            default:
                return f;
        }
    }

    @Override
    public double[] score0(double[] row, double[] preds) {
        return score0(row, 0.0, preds);
    }

    public String[] leaf_node_assignment(double[] row) {
        return getDecisionPath(row);
    }

    @Override
    public PredictContributions makeContributionsPredictor() {
        if (_nclasses > 2) {
            throw new UnsupportedOperationException("Predicting contributions for multinomial classification problems is not yet supported.");
        }
        SharedTreeGraph graph = _computeGraph(-1);
        final SharedTreeNode[] empty = new SharedTreeNode[0];
        List<TreeSHAPPredictor<double[]>> treeSHAPs = new ArrayList<>(graph.subgraphArray.size());
        for (SharedTreeSubgraph tree : graph.subgraphArray) {
            SharedTreeNode[] nodes = tree.nodesArray.toArray(empty);
            treeSHAPs.add(new TreeSHAP<>(nodes, nodes, 0));
        }
        TreeSHAPPredictor<double[]> predictor = new TreeSHAPEnsemble<>(treeSHAPs, (float) _init_f);
        return new GbmContributionsPredictor(predictor);
    }

    private final class GbmContributionsPredictor implements PredictContributions {
        private final TreeSHAPPredictor<double[]> _treeSHAPPredictor;
        private final Object _workspace;

        private GbmContributionsPredictor(TreeSHAPPredictor<double[]> treeSHAPPredictor) {
            _treeSHAPPredictor = treeSHAPPredictor;
            _workspace = _treeSHAPPredictor.makeWorkspace();
        }

        @Override
        public float[] calculateContributions(double[] input) {
            float[] contribs = new float[nfeatures() + 1];
            return  _treeSHAPPredictor.calculateContributions(input, contribs, 0, -1, _workspace);
        }
    }

}
