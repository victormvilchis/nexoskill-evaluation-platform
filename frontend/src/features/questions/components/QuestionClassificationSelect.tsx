interface QuestionClassificationOption {
  value: string
  label: string
}

interface QuestionClassificationSelectProps {
  id: string
  label: string
  value: string
  options: QuestionClassificationOption[]
  help: string
  required?: boolean
  disabled?: boolean
  onChange: (value: string) => void
}

export function QuestionClassificationSelect({
  id,
  label,
  value,
  options,
  help,
  required = false,
  disabled = false,
  onChange
}: QuestionClassificationSelectProps) {
  return (
    <div className="form-field question-classification-select">
      <label htmlFor={id}>{label}</label>
      <select
        id={id}
        required={required}
        disabled={disabled}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      >
        {options.map((option) => (
          <option key={option.value} value={option.value}>{option.label}</option>
        ))}
      </select>
      <small>{help}</small>
    </div>
  )
}
