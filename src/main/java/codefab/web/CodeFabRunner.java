package codefab.web;

import codefab.RunResult;

@FunctionalInterface
public interface CodeFabRunner {
    RunResult run(String source);
}
