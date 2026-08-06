import type { ComponentProps } from "react";

interface AuthFieldProps extends ComponentProps<"input"> {
  label: string;
  error?: string;
}

export function AuthField({
  id,
  label,
  error,
  className,
  ...inputProps
}: AuthFieldProps) {
  const errorId = error ? `${id}-error` : undefined;

  return (
    <div className="space-y-2">
      <label htmlFor={id} className="text-sm font-semibold text-slate-800">
        {label}
      </label>
      <input
        {...inputProps}
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={errorId}
        className={`h-12 w-full rounded-xl border bg-white px-4 text-[15px] text-slate-950 outline-none transition placeholder:text-slate-400 focus:ring-4 ${
          error
            ? "border-rose-400 focus:border-rose-500 focus:ring-rose-100"
            : "border-slate-200 focus:border-blue-500 focus:ring-blue-100"
        } ${className ?? ""}`}
      />
      {error ? (
        <p id={errorId} className="text-sm text-rose-600">
          {error}
        </p>
      ) : null}
    </div>
  );
}
