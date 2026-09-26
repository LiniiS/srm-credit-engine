package com.srm.creditengine.pricing.service;

import com.srm.creditengine.currency.domain.port.BaseRateCurrencyNotSupportedException;
import com.srm.creditengine.currency.domain.port.BaseRateNotFoundException;
import com.srm.creditengine.currency.domain.port.BaseRateQuery;
import com.srm.creditengine.currency.domain.port.BaseRateQueryException;
import com.srm.creditengine.currency.domain.port.CurrencyMetadataNotFoundException;
import com.srm.creditengine.currency.domain.port.CurrencyMetadataQuery;
import com.srm.creditengine.currency.domain.port.CurrencyMetadataQueryException;
import com.srm.creditengine.pricing.domain.BusinessCalendar;
import com.srm.creditengine.pricing.domain.BusinessCalendarNotAvailableException;
import com.srm.creditengine.pricing.domain.DecimalPower;
import com.srm.creditengine.pricing.domain.DueDateBeforeCalculationDateException;
import com.srm.creditengine.pricing.domain.PricingCalculation;
import com.srm.creditengine.pricing.domain.PricingCalculationException;
import com.srm.creditengine.pricing.domain.PricingStrategyNotConfiguredException;
import com.srm.creditengine.pricing.domain.ReceivableTypeInactiveException;
import com.srm.creditengine.pricing.domain.ReceivableTypeNotFoundException;
import com.srm.creditengine.pricing.domain.ReceivableTypeQueryException;
import com.srm.creditengine.pricing.domain.port.ReceivableTypePricingResolver;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public final class PricingSimulationService {
  private static final Logger LOGGER = LoggerFactory.getLogger(PricingSimulationService.class);

  private final ReceivableTypePricingResolver pricingResolver;
  private final BaseRateQuery baseRateQuery;
  private final CurrencyMetadataQuery currencyMetadataQuery;
  private final BusinessCalendar businessCalendar;
  private final PricingCalculation calculation;
  private final MeterRegistry meterRegistry;

  PricingSimulationService(
      ReceivableTypePricingResolver pricingResolver,
      BaseRateQuery baseRateQuery,
      CurrencyMetadataQuery currencyMetadataQuery,
      BusinessCalendar businessCalendar,
      DecimalPower decimalPower,
      MeterRegistry meterRegistry) {
    this.pricingResolver = pricingResolver;
    this.baseRateQuery = baseRateQuery;
    this.currencyMetadataQuery = currencyMetadataQuery;
    this.businessCalendar = businessCalendar;
    this.calculation = new PricingCalculation(decimalPower);
    this.meterRegistry = meterRegistry;
  }

  public PricingSimulationResult simulate(PricingSimulationCommand command) {
    Objects.requireNonNull(command, "command");
    var sample = Timer.start(meterRegistry);
    try {
      var result = calculate(command);
      LOGGER.info(
          "Pricing simulation completed outcome=success currency={} receivableType={}",
          result.currency(),
          result.receivableTypeCode());
      return result;
    } catch (BusinessCalendarNotAvailableException exception) {
      throw failure(
          exception,
          "BUSINESS_CALENDAR_NOT_AVAILABLE",
          "Business calendar not available",
          PricingSimulationException.Kind.UNPROCESSABLE);
    } catch (DueDateBeforeCalculationDateException exception) {
      throw failure(
          exception,
          "DUE_DATE_BEFORE_CALCULATION_DATE",
          "Adjusted due date is before calculation date",
          PricingSimulationException.Kind.UNPROCESSABLE);
    } catch (BaseRateCurrencyNotSupportedException | CurrencyMetadataNotFoundException exception) {
      throw failure(
          exception,
          "CURRENCY_NOT_SUPPORTED",
          "Currency not supported",
          PricingSimulationException.Kind.BAD_REQUEST);
    } catch (BaseRateNotFoundException exception) {
      throw failure(
          exception,
          "BASE_RATE_NOT_FOUND",
          "Base rate not found",
          PricingSimulationException.Kind.NOT_FOUND);
    } catch (ReceivableTypeNotFoundException exception) {
      throw failure(
          exception,
          "RECEIVABLE_TYPE_NOT_FOUND",
          "Receivable type not found",
          PricingSimulationException.Kind.NOT_FOUND);
    } catch (ReceivableTypeInactiveException exception) {
      throw failure(
          exception,
          "RECEIVABLE_TYPE_INACTIVE",
          "Receivable type is inactive",
          PricingSimulationException.Kind.UNPROCESSABLE);
    } catch (PricingStrategyNotConfiguredException exception) {
      throw failure(
          exception,
          "PRICING_STRATEGY_NOT_CONFIGURED",
          "Pricing strategy is not configured",
          PricingSimulationException.Kind.INTERNAL);
    } catch (PricingCalculationException
        | BaseRateQueryException
        | CurrencyMetadataQueryException
        | ReceivableTypeQueryException exception) {
      throw failure(
          exception,
          "PRICING_CALCULATION_FAILED",
          "Pricing calculation failed",
          PricingSimulationException.Kind.INTERNAL);
    } catch (RuntimeException exception) {
      LOGGER.info("Pricing simulation completed outcome=failure code={}", codeOf(exception));
      throw exception;
    } finally {
      sample.stop(meterRegistry.timer("srm.pricing.duration"));
    }
  }

  private PricingSimulationException failure(
      RuntimeException cause, String code, String title, PricingSimulationException.Kind kind) {
    LOGGER.info("Pricing simulation completed outcome=failure code={}", code);
    return new PricingSimulationException(code, title, kind, cause);
  }

  private PricingSimulationResult calculate(PricingSimulationCommand command) {
    if (command.faceValue() == null || command.faceValue().signum() <= 0) {
      throw new IllegalArgumentException("faceValue must be positive");
    }
    Objects.requireNonNull(command.currency(), "currency");
    Objects.requireNonNull(command.receivableTypeCode(), "receivableTypeCode");
    Objects.requireNonNull(command.calculationDate(), "calculationDate");
    Objects.requireNonNull(command.dueDate(), "dueDate");
    businessCalendar.requireCovered(command.calculationDate());
    var adjustedDueDate = businessCalendar.nextOrSameBusinessDay(command.dueDate());
    if (adjustedDueDate.isBefore(command.calculationDate())) {
      throw new DueDateBeforeCalculationDateException();
    }
    var metadata = currencyMetadataQuery.find(command.currency());
    var resolvedPricing = pricingResolver.resolve(command.receivableTypeCode());
    var baseRate = metadata.findApplicableBaseRate(baseRateQuery, command.calculationDate());
    var termDays = ChronoUnit.DAYS.between(command.calculationDate(), adjustedDueDate);
    var values =
        calculation.calculate(
            command.faceValue(),
            termDays,
            baseRate.rateMonthly(),
            resolvedPricing.monthlySpread(),
            metadata.minorUnits());
    return new PricingSimulationResult(
        command.faceValue(),
        command.currency(),
        resolvedPricing.receivableTypeCode().value(),
        command.calculationDate(),
        command.dueDate(),
        adjustedDueDate,
        termDays,
        values.termMonths(),
        baseRate.rateMonthly(),
        baseRate.id(),
        baseRate.source(),
        resolvedPricing.monthlySpread(),
        values.monthlyRate(),
        values.presentValue(),
        values.discount());
  }

  private String codeOf(RuntimeException exception) {
    var message = exception.getMessage();
    if (message == null || !message.matches("[A-Z][A-Z0-9_]*(?::.*)?")) {
      return "UNEXPECTED";
    }
    var separator = message.indexOf(':');
    return separator < 0 ? message : message.substring(0, separator);
  }
}
