package io.runebox.revtools.mapper.classifier;

import io.runebox.revtools.mapper.type.ClassEnvironment;

public interface IClassifier<T> {
	String getName();
	double getWeight();
	double getScore(T a, T b, ClassEnvironment env);
}
