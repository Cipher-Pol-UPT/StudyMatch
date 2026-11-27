import { Plus } from 'lucide-react';
import { useRef, useState, type CSSProperties, type DragEvent } from 'react';
import { ChatFilePaper } from './ChatFiles';

export interface ChatDropProps {
  // "Drop to send to Rui"
  title: string;
  // "Up to 5 files, 10 MB each"
  hint: string;
  onDrop: (files: File[]) => void;
}

const hasFiles = (event: DragEvent) => Array.from(event.dataTransfer.types).includes('Files');

// files dragged over the chat: the papers fan out over a dashed drop zone
export function useChatDrop(drop: ChatDropProps | undefined) {
  const [over, setOver] = useState(false);
  // enter and leave fire for every child, so it counts them
  const depth = useRef(0);
  if (!drop) return { handlers: {}, zone: null };
  const handlers = {
    onDragEnter: (event: DragEvent) => {
      if (!hasFiles(event)) return;
      event.preventDefault();
      depth.current += 1;
      setOver(true);
    },
    onDragOver: (event: DragEvent) => {
      if (!hasFiles(event)) return;
      event.preventDefault();
      event.dataTransfer.dropEffect = 'copy';
    },
    onDragLeave: (event: DragEvent) => {
      if (!hasFiles(event)) return;
      depth.current = Math.max(0, depth.current - 1);
      if (!depth.current) setOver(false);
    },
    onDrop: (event: DragEvent) => {
      if (!hasFiles(event)) return;
      event.preventDefault();
      depth.current = 0;
      setOver(false);
      drop.onDrop(Array.from(event.dataTransfer.files));
    },
  };
  const zone = over && (
    <div className="sm-drop" aria-hidden>
      <div className="sm-drop__papers">
        {(['image', 'pdf', 'doc'] as const).map((kind, index) => (
          <span key={kind} className="sm-drop__paper" style={{ '--i': index } as CSSProperties}>
            <ChatFilePaper kind={kind} tag={kind === 'image' ? 'PNG' : kind === 'pdf' ? 'PDF' : 'DOCX'} />
          </span>
        ))}
        <span className="sm-drop__plus"><Plus size={18} strokeWidth={2.5} /></span>
      </div>
      <b>{drop.title}</b>
      <span>{drop.hint}</span>
    </div>
  );
  return { handlers, zone };
}
