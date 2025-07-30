import React from "react";
import styled from "styled-components";

const COLORS = {
  background: "rgba(18,18,18,0.85)",
  surface: "#1E1E1E",
  onBackground: "#E0E0E0",
};

interface ModalProps {
  open: boolean;
  onClose: () => void;
  children: React.ReactNode;
}

const Overlay = styled.div`
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: ${COLORS.background};
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
`;

const ModalBox = styled.div`
  background: ${COLORS.surface};
  color: ${COLORS.onBackground};
  border-radius: 16px;
  box-shadow: 0 4px 24px rgba(0,0,0,0.18);
  padding: 32px 24px;
  min-width: 320px;
  max-width: 90vw;
  font-family: 'Noto Sans KR', 'Roboto', 'Apple SD Gothic Neo', sans-serif;
`;

export const Modal: React.FC<ModalProps> = ({ open, onClose, children }) => {
  if (!open) return null;
  return (
    <Overlay onClick={onClose}>
      <ModalBox onClick={e => e.stopPropagation()}>
        {children}
      </ModalBox>
    </Overlay>
  );
}; 