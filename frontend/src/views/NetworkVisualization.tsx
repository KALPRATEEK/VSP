import { useEffect, useRef } from 'react';

interface Node {
  id: string;
  currentLeader: string | null;
  messagesSent: number;
  messagesReceived: number;
  isLeader: boolean;
}

interface Edge {
  from: string;
  to: string;
}

interface Props {
  nodes: Node[];
  edges: Edge[];
}

export function NetworkVisualization({ nodes, edges }: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    // Clear canvas
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    // Calculate node positions in a circle
    const centerX = canvas.width / 2;
    const centerY = canvas.height / 2;
    const radius = Math.min(centerX, centerY) - 60;

    const nodePositions = new Map<string, { x: number; y: number }>();
    nodes.forEach((node, index) => {
      const angle = (2 * Math.PI * index) / nodes.length - Math.PI / 2;
      const x = centerX + radius * Math.cos(angle);
      const y = centerY + radius * Math.sin(angle);
      nodePositions.set(node.id, { x, y });
    });

    // Draw edges
    ctx.strokeStyle = '#64748b';
    ctx.lineWidth = 2;
    edges.forEach(edge => {
      const from = nodePositions.get(edge.from);
      const to = nodePositions.get(edge.to);
      if (from && to) {
        ctx.beginPath();
        ctx.moveTo(from.x, from.y);
        ctx.lineTo(to.x, to.y);
        ctx.stroke();
      }
    });

    // Draw nodes
    nodes.forEach(node => {
      const pos = nodePositions.get(node.id);
      if (!pos) return;

      // Draw circle
      ctx.beginPath();
      ctx.arc(pos.x, pos.y, 30, 0, 2 * Math.PI);
      ctx.fillStyle = node.isLeader ? '#fbbf24' : '#3b82f6';
      ctx.fill();
      ctx.strokeStyle = '#1e293b';
      ctx.lineWidth = 3;
      ctx.stroke();

      // Draw node ID
      ctx.fillStyle = '#fff';
      ctx.font = 'bold 12px sans-serif';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      const nodeNum = node.id.split('-')[1];
      ctx.fillText(nodeNum, pos.x, pos.y);

      // Draw leader badge
      if (node.isLeader) {
        ctx.font = '20px sans-serif';
        ctx.fillText('👑', pos.x, pos.y - 45);
      }

      // Draw stats below node
      ctx.fillStyle = '#1e293b';
      ctx.font = '10px sans-serif';
      ctx.fillText(`S:${node.messagesSent} R:${node.messagesReceived}`, pos.x, pos.y + 45);
    });
  }, [nodes, edges]);

  return (
    <div className="network-visualization">
      <canvas 
        ref={canvasRef} 
        width={800} 
        height={600}
        style={{ width: '100%', maxWidth: '800px', border: '1px solid #e2e8f0' }}
      />
    </div>
  );
}
