import { ArrowUp, Square } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { Button } from '@/components/ui/button';

const MAX_HEIGHT = 200;

export function Composer({
  onSend,
  onStop,
  busy,
  disabled,
  placeholder,
}: {
  onSend: (text: string) => void;
  onStop: () => void;
  busy: boolean;
  disabled?: boolean;
  placeholder?: string;
}) {
  const [value, setValue] = useState('');
  const ref = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    const element = ref.current;
    if (!element) return;
    element.style.height = 'auto';
    element.style.height = `${Math.min(element.scrollHeight, MAX_HEIGHT)}px`;
  }, [value]);

  const submit = () => {
    const text = value.trim();
    if (!text || busy || disabled) return;
    onSend(text);
    setValue('');
  };

  return (
    <div className="flex items-end gap-2 rounded-xl border bg-card p-2 shadow-card transition-shadow focus-within:border-ring focus-within:ring-2 focus-within:ring-ring/20">
      <textarea
        ref={ref}
        rows={1}
        maxLength={4000}
        value={value}
        disabled={disabled}
        aria-label="Message"
        placeholder={placeholder ?? 'Ask anything about your notes…'}
        onChange={(event) => setValue(event.target.value)}
        onKeyDown={(event) => {
          if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
            event.preventDefault();
            submit();
          }
        }}
        className="scroll-thin max-h-[200px] flex-1 resize-none bg-transparent px-2 py-1.5 text-[15px] leading-6 outline-none placeholder:text-muted-foreground disabled:opacity-50"
      />
      {busy ? (
        <Button size="icon-sm" variant="secondary" aria-label="Stop generating" onClick={onStop}>
          <Square className="fill-current" />
        </Button>
      ) : (
        <Button size="icon-sm" aria-label="Send message" disabled={!value.trim() || disabled} onClick={submit}>
          <ArrowUp />
        </Button>
      )}
    </div>
  );
}
