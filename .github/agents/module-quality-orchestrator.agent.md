Use `git diff main...HEAD -- agentforge4j-<module>` as the review scope.

Only inspect files changed by that diff, plus directly related files needed for context.

Run the steps in this order:
1. Apply Javadocs agent to changed public APIs
2. Apply unit-test agent to changed behavior
3. Run `mvn -pl agentforge4j-<module> -am test`
4. Apply review agent to the resulting diff
5. Generate changelog entry from final diff
