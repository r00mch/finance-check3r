package com.financetracker.report;

/**
 * Scores how unusual an expense is compared with the other expenses of its category (a z-score:
 * how many standard deviations above the category's typical amount).
 *
 * <p>The "peers" are all <i>other</i> expenses of the category (leave-one-out). Including the expense
 * itself would let a single huge purchase inflate the deviation it is measured against and hide itself.
 */
final class AnomalyDetector {

    /**
     * Floor for the spread, as a share of the mean. If every earlier rent payment was exactly 25 000,
     * the standard deviation is 0 and any change would score infinity; 5 % of the mean keeps scores sane.
     */
    static final double MIN_RELATIVE_SPREAD = 0.05;

    private AnomalyDetector() {
    }

    /**
     * @param peerMean       average of the peers' amounts
     * @param peerMeanSquare average of the peers' squared amounts (gives the variance as E[x²] - E[x]²)
     */
    static double zScore(long amount, double peerMean, double peerMeanSquare) {
        double variance = Math.max(0, peerMeanSquare - peerMean * peerMean);
        double spread = Math.max(Math.sqrt(variance), MIN_RELATIVE_SPREAD * peerMean);
        return spread == 0 ? 0 : (amount - peerMean) / spread;
    }
}
