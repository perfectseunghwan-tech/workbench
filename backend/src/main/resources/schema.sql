CREATE TABLE IF NOT EXISTS investment_strategy_trade (
    strategy_id BIGINT NOT NULL REFERENCES investment_strategy(strategy_id) ON DELETE CASCADE,
    trade_id BIGINT NOT NULL REFERENCES stock_trade(trade_id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (strategy_id, trade_id)
);

CREATE INDEX IF NOT EXISTS idx_investment_strategy_trade_trade_id
    ON investment_strategy_trade(trade_id);

CREATE TABLE IF NOT EXISTS stock_balance_trade_application (
    trade_id BIGINT PRIMARY KEY REFERENCES stock_trade(trade_id),
    account_id BIGINT NOT NULL REFERENCES investment_account(account_id),
    stock_id BIGINT NOT NULL REFERENCES stock_master(stock_id),
    trade_type VARCHAR(4) NOT NULL CHECK (trade_type IN ('BUY', 'SELL')),
    applied_quantity NUMERIC(20, 6) NOT NULL CHECK (applied_quantity > 0),
    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_stock_balance_trade_application_account
    ON stock_balance_trade_application(account_id, stock_id);
