# Util Module Review (2026-04-28)

## Findings
- Minor: `notBlank(...)` should return delegated result for consistency
- Consider removing commons-lang3 dependency
- Add null-safety for Supplier in validation methods
- Rename `higher` → `upper`
- Improve null checks in `isBetween`

## Decisions
- [ ] Fix `notBlank` return pattern

## Notes
Reviewed with custom agent + manual validation.
