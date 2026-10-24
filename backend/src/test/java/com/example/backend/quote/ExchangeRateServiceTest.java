package com.example.backend.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.backend.trade.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ExchangeRateServiceTest {
    private final TossPriceClient client = org.mockito.Mockito.mock(TossPriceClient.class);
    private final ExchangeRateService service = new ExchangeRateService(client);
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void readsUsdToKrwRate() throws Exception {
        when(client.exchangeRate()).thenReturn(mapper.readTree("""
                {"result":{"baseCurrency":"USD","quoteCurrency":"KRW","rate":"1380.5"}}
                """));

        assertThat(service.usdToKrw().rate()).isEqualByComparingTo("1380.5");
    }

    @Test
    void rejectsWrongCurrency() throws Exception {
        when(client.exchangeRate()).thenReturn(mapper.readTree("""
                {"result":{"baseCurrency":"KRW","quoteCurrency":"USD","rate":"0.0007"}}
                """));

        assertThatThrownBy(service::usdToKrw).isInstanceOf(ApiException.class);
    }
}
