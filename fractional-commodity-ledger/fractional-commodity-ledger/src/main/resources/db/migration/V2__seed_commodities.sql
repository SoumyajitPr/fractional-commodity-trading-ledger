-- Sample commodities, priced per gram in INR. Prices are illustrative; an ADMIN can
-- change them with PUT /api/commodities/{symbol}/price.
INSERT INTO commodities (symbol, name, unit, price_per_unit) VALUES ('GOLD', 'Gold 24K', 'GRAM', 12500.00);
INSERT INTO commodities (symbol, name, unit, price_per_unit) VALUES ('SILVER', 'Silver 999', 'GRAM', 160.00);
INSERT INTO commodities (symbol, name, unit, price_per_unit) VALUES ('PLATINUM', 'Platinum 950', 'GRAM', 3200.00);
