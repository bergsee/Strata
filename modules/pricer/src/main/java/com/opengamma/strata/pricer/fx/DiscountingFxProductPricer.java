/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.pricer.fx;

import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.currency.FxRate;
import com.opengamma.strata.basics.currency.MultiCurrencyAmount;
import com.opengamma.strata.finance.fx.ExpandedFx;
import com.opengamma.strata.finance.fx.FxPayment;
import com.opengamma.strata.finance.fx.FxProduct;
import com.opengamma.strata.market.sensitivity.PointSensitivities;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;
import com.opengamma.strata.market.value.DiscountFactors;
import com.opengamma.strata.pricer.rate.RatesProvider;

/**
 * Pricer for foreign exchange transaction products.
 * <p>
 * This function provides the ability to price an {@link FxProduct}.
 */
public class DiscountingFxProductPricer {

  /**
   * Default implementation.
   */
  public static final DiscountingFxProductPricer DEFAULT = new DiscountingFxProductPricer();

  /**
   * Creates an instance.
   */
  public DiscountingFxProductPricer() {
  }

  //-------------------------------------------------------------------------
  /**
   * Computes the present value of the FX product by discounting each payment in its own currency.
   * 
   * @param product  the product to price
   * @param provider  the rates provider
   * @return the present value in the two natural currencies
   */
  public MultiCurrencyAmount presentValue(FxProduct product, RatesProvider provider) {
    ExpandedFx fx = product.expand();
    if (provider.getValuationDate().isAfter(fx.getPaymentDate())) {
      return MultiCurrencyAmount.empty();
    }
    CurrencyAmount pv1 = presentValue(fx.getBaseCurrencyPayment(), provider);
    CurrencyAmount pv2 = presentValue(fx.getCounterCurrencyPayment(), provider);
    return MultiCurrencyAmount.of(pv1, pv2);
  }

  /**
   * Computes the present value of the payment by discounting. 
   * 
   * @param payment  the payment to price
   * @param provider  the rates provider
   * @return the present value
   */
  public CurrencyAmount presentValue(FxPayment payment, RatesProvider provider) {
    return payment.getValue().multipliedBy(provider.discountFactor(payment.getCurrency(), payment.getPaymentDate()));
  }

  /**
   * Computes the currency exposure by discounting each payment in its own currency.
   * 
   * @param product  the product to price
   * @param provider  the rates provider
   * @return the currency exposure
   */
  public MultiCurrencyAmount currencyExposure(FxProduct product, RatesProvider provider) {
    return presentValue(product, provider);
  }

  /**
   * The par spread is the spread that should be added to the FX points to have a zero value.
   * 
   * @param product  the product to price
   * @param provider  the rates provider
   * @return the spread
   */
  public double parSpread(FxProduct product, RatesProvider provider) {
    ExpandedFx fx = product.expand();
    FxPayment basePayment = fx.getBaseCurrencyPayment();
    FxPayment counterPayment = fx.getCounterCurrencyPayment();
    MultiCurrencyAmount pv = presentValue(fx, provider);
    double pvCounterCcy = pv.convertedTo(counterPayment.getCurrency(), provider).getAmount();
    double dfEnd = provider.discountFactor(counterPayment.getCurrency(), fx.getPaymentDate());
    double notionalBaseCcy = basePayment.getAmount();
    return pvCounterCcy / (notionalBaseCcy * dfEnd);
  }

  /**
   * Computes the forward exchange rate.
   * 
   * @param product  the product to price
   * @param provider  the rates provider
   * @return the forward rate
   */
  public FxRate forwardFxRate(FxProduct product, RatesProvider provider) {
    ExpandedFx fx = product.expand();
    FxPayment basePayment = fx.getBaseCurrencyPayment();
    FxPayment counterPayment = fx.getCounterCurrencyPayment();
    // TODO: domestic/foreign vs base/counter?
    double dfDomestic = provider.discountFactor(counterPayment.getCurrency(), counterPayment.getPaymentDate());
    double dfForeign = provider.discountFactor(basePayment.getCurrency(), basePayment.getPaymentDate());
    double spot = provider.fxRate(basePayment.getCurrency(), counterPayment.getCurrency());
    return FxRate.of(basePayment.getCurrency(), counterPayment.getCurrency(), spot * dfForeign / dfDomestic);
  }

  //-------------------------------------------------------------------------
  /**
   * Compute the present value curve sensitivity of the FX product.
   * <p>
   * The present value sensitivity of the product is the sensitivity of the present value to
   * the underlying curves.
   * 
   * @param product  the product to price
   * @param provider  the rates provider
   * @return the point sensitivity of the present value
   */
  public PointSensitivities presentValueSensitivity(FxProduct product, RatesProvider provider) {
    ExpandedFx fx = product.expand();
    PointSensitivityBuilder pvcs1 = presentValueSensitivity(fx.getBaseCurrencyPayment(), provider);
    PointSensitivityBuilder pvcs2 = presentValueSensitivity(fx.getCounterCurrencyPayment(), provider);
    return pvcs1.combinedWith(pvcs2).build();
  }

  /**
   * Compute the present value curve sensitivity of the payment.
   * <p>
   * The present value sensitivity of the product is the sensitivity of the present value to
   * the underlying curves.
   * 
   * @param payment  the payment to price
   * @param provider  the rates provider
   * @return the point sensitivity of the present value
   */
  public PointSensitivityBuilder presentValueSensitivity(FxPayment payment, final RatesProvider provider) {
    DiscountFactors discountFactors = provider.discountFactors(payment.getCurrency());
    return discountFactors.zeroRatePointSensitivity(payment.getPaymentDate())
        .multipliedBy(payment.getAmount());
  }

}
