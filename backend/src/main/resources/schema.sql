CREATE TABLE IF NOT EXISTS investment_strategy_trade (
    strategy_id BIGINT NOT NULL REFERENCES investment_strategy(strategy_id) ON DELETE CASCADE,
    trade_id BIGINT NOT NULL REFERENCES stock_trade(trade_id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (strategy_id, trade_id)
);

CREATE INDEX IF NOT EXISTS idx_investment_strategy_trade_trade_id
    ON investment_strategy_trade(trade_id);
