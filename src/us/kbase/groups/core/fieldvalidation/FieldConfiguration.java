package us.kbase.groups.core.fieldvalidation;

/** A configuration for a {@link CustomField}.
 * @author gaprice@lbl.gov
 *
 */
public class FieldConfiguration {
	
	private final boolean isNumberedField;
	private final boolean isPublicField;
	private final boolean isMinimalViewField;
	
	private FieldConfiguration(
			final boolean isNumberedField,
			final boolean isPublicField,
			final boolean isMinimalViewField) {
		this.isNumberedField = isNumberedField;
		this.isPublicField = isPublicField;
		this.isMinimalViewField = isMinimalViewField;
	}

	/** Get whether the field may be a numbered field, as specified by
	 * {@link NumberedCustomField#isNumberedField()}.
	 * @return true if the field is a numbered field.
	 */
	public boolean isNumberedField() {
		return isNumberedField;
	}
	
	/** Get whether the field is a public field and should be available to all users regardless
	 * of appropriate authorization.
	 * @return whether the field is public.
	 */
	public boolean isPublicField() {
		return isPublicField;
	}
	
	/** Get whether the field should be shown in minimal views of the containing object.
	 * @return whether the field show be shown in minimal views.
	 */
	public boolean isMinimalViewField() {
		return isMinimalViewField;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (isMinimalViewField ? 1231 : 1237);
		result = prime * result + (isNumberedField ? 1231 : 1237);
		result = prime * result + (isPublicField ? 1231 : 1237);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		FieldConfiguration other = (FieldConfiguration) obj;
		if (isMinimalViewField != other.isMinimalViewField) {
			return false;
		}
		if (isNumberedField != other.isNumberedField) {
			return false;
		}
		if (isPublicField != other.isPublicField) {
			return false;
		}
		return true;
	}
	
	/** Get a builder for a {@link FieldConfiguration}.
	 * @return the builder.
	 */
	public static Builder getBuilder() {
		return new Builder();
	}
	
	/** A builder for a {@link FieldConfiguration}.
	 * @author gaprice@lbl.gov
	 *
	 */
	public static class Builder {
		
		private boolean isNumberedField = false;
		private boolean isPublicField = false;
		private boolean isMinimalViewField = false;
		
		private Builder() {}
		
		/** Set whether this field is a numbered field.
		 * @param isNumberedField whether the field may be a numbered field, as specified by
		 * {@link NumberedCustomField#isNumberedField()}. Null results in a false (the default)
		 * value.
		 * @return this builder.
		 */
		public Builder withNullableIsNumberedField(final Boolean isNumberedField) {
			this.isNumberedField = bool(isNumberedField);
			return this;
		}
		
		/** Set whether the field is a public field and should be available to all users without
		 * regard for appropriate authorization.
		 * @param isPublicField whether the field is public. Null results in a false
		 * (the default) value.
		 * @return this builder.
		 */
		public Builder withNullableIsPublicField(final Boolean isPublicField) {
			this.isPublicField = bool(isPublicField);
			return this;
		}
		
		/** Set whether the field should be shown in minimal views of the containing object.
		 * @param isMinimalViewField whether the field show be shown in minimal views. Null
		 * results in a false (the default) value.
		 * @return this builder.
		 */
		public Builder withNullableIsMinimalViewField(final Boolean isMinimalViewField) {
			this.isMinimalViewField = bool(isMinimalViewField);
			return this;
		}
		
		// null == false
		private boolean bool(final Boolean b) {
			return b != null && b;
		}
		
		/** Build the field configuration.
		 * @return a new configuration.
		 */
		public FieldConfiguration build() {
			return new FieldConfiguration(isNumberedField, isPublicField, isMinimalViewField);
		}
	}


}
