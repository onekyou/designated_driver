import React from "react";
import styled, { css } from "styled-components";

const COLORS = {
  primary: "#FFAB00",
  secondary: "#6650A4",
  background: "#121212",
  surface: "#1E1E1E",
  onPrimary: "#000000",
  onBackground: "#E0E0E0",
  disabled: "#404040",
};

type ButtonVariant = "default" | "primary" | "secondary" | "disabled";
type ButtonSize = "large" | "medium" | "small";

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  children: React.ReactNode;
}

const sizeStyles = {
  large: css`
    font-size: 18px;
    padding: 14px 32px;
  `,
  medium: css`
    font-size: 16px;
    padding: 10px 24px;
  `,
  small: css`
    font-size: 14px;
    padding: 6px 16px;
  `,
};

const variantStyles = {
  default: css`
    background: ${COLORS.surface};
    color: ${COLORS.onBackground};
    border: 1px solid ${COLORS.onBackground};
  `,
  primary: css`
    background: ${COLORS.primary};
    color: ${COLORS.onPrimary};
    border: none;
  `,
  secondary: css`
    background: ${COLORS.secondary};
    color: #fff;
    border: none;
  `,
  disabled: css`
    background: ${COLORS.disabled};
    color: #888;
    border: none;
    cursor: not-allowed;
    opacity: 0.7;
  `,
};

const StyledButton = styled.button<{
  $variant: ButtonVariant;
  $size: ButtonSize;
  disabled?: boolean;
}>`
  border-radius: 8px;
  font-family: 'Noto Sans KR', 'Roboto', 'Apple SD Gothic Neo', sans-serif;
  font-weight: 500;
  text-transform: uppercase;
  transition: background 0.2s, color 0.2s, box-shadow 0.2s;
  outline: none;
  ${(props) => sizeStyles[props.$size]}
  ${(props) => variantStyles[props.disabled ? "disabled" : props.$variant]}
  &:hover:not(:disabled) {
    box-shadow: 0 2px 8px rgba(0,0,0,0.12);
    filter: brightness(0.97);
  }
`;

export const Button: React.FC<ButtonProps> = ({
  variant = "default",
  size = "medium",
  disabled,
  children,
  ...rest
}) => (
  <StyledButton
    $variant={variant}
    $size={size}
    disabled={disabled}
    {...rest}
  >
    {children}
  </StyledButton>
); 