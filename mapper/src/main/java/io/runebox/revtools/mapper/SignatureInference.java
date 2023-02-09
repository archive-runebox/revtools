package io.runebox.revtools.mapper;

import io.runebox.revtools.mapper.type.*;

import java.util.HashSet;
import java.util.Set;

public final class SignatureInference<T> {
	public static void process(ClassEnvironment env) {
		Set<String> missingParams = new HashSet<>();
		Set<String> shadowedParams = new HashSet<>();

		for (ClassInstance cls : env.getClassesA()) {
			if (!cls.isInput() || cls.getSignature() != null) continue;

			// find type variables needed by own fields (can't declare their own type variables, so must be from the class)

			for (FieldInstance field : cls.getFields()) {
				Signature.FieldSignature sig = field.getSignature();
				if (sig == null) continue;

				processRefTypeSig(sig.getCls(), shadowedParams, missingParams);
			}

			// find type variables needed by own methods and not declared by them

			for (MethodInstance method : cls.getMethods()) {
				Signature.MethodSignature sig = method.getSignature();
				if (sig == null) continue;

				if (sig.getTypeParameters() != null) {
					for (Signature.TypeParameter typeParem : sig.getTypeParameters()) {
						shadowedParams.add(typeParem.getIdentifier());
					}
				}

				for (Signature.JavaTypeSignature arg : sig.getArgs()) {
					Signature.ReferenceTypeSignature argRefType = arg.getCls();
					if (argRefType != null) processRefTypeSig(argRefType, shadowedParams, missingParams);
				}

				if (sig.getResult() != null) {
					if (sig.getResult().getCls() != null) processRefTypeSig(sig.getResult().getCls(), shadowedParams, missingParams);
				}

				if (sig.getThrowsSignatures() != null) {
					for (Signature.ThrowsSignature throwsSig : sig.getThrowsSignatures()) {
						if (throwsSig.getCls() != null) {
							processClsTypeSig(throwsSig.getCls(), shadowedParams, missingParams);
						} else { // throwsSig.getVar() != null
							if (!shadowedParams.contains(throwsSig.getVar())) {
								missingParams.add(throwsSig.getVar());
							}
						}
					}
				}

				shadowedParams.clear();
			}

			missingParams.clear();
		}
	}

	private static void processRefTypeSig(Signature.ReferenceTypeSignature sig, Set<String> shadowedParams, Set<String> missingParams) {
		if (sig.getCls() != null) {
			processClsTypeSig(sig.getCls(), shadowedParams, missingParams);
		} else if (sig.getVar() != null) {
			if (!shadowedParams.contains(sig.getVar())) {
				missingParams.add(sig.getVar());
			}
		} else { // sig.getArrayElemCls() != null
			if (sig.getArrayElemCls().getCls() != null) {
				processRefTypeSig(sig.getArrayElemCls().getCls(), shadowedParams, missingParams);
			}
		}
	}

	private static void processClsTypeSig(Signature.ClassTypeSignature sig, Set<String> shadowedParams, Set<String> missingParams) {
		if (sig.getTypeArguments() != null) {
			for (Signature.TypeArgument typeArg : sig.getTypeArguments()) {
				if (typeArg.getCls() != null) {
					processRefTypeSig(typeArg.getCls(), shadowedParams, missingParams);
				}
			}
		}

		if (sig.getSuffixes() != null) {
			for (Signature.SimpleClassTypeSignature ts : sig.getSuffixes()) {
				if (ts.getTypeArguments() != null) {
					for (Signature.TypeArgument typeArg : ts.getTypeArguments()) {
						if (typeArg.getCls() != null) {
							processRefTypeSig(typeArg.getCls(), shadowedParams, missingParams);
						}
					}
				}
			}
		}
	}
}
